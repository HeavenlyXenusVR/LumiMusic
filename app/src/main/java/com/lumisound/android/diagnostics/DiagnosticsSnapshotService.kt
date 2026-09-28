package com.lumisound.android.diagnostics

import android.app.ActivityManager
import android.content.Context
import android.os.BatteryManager
import android.os.Build
import android.os.PowerManager
import com.lumisound.android.BuildConfig
import com.lumisound.android.bridge.AccountState
import com.lumisound.android.bridge.BridgeConfig
import com.lumisound.android.bridge.TokenStore
import com.lumisound.android.data.db.LumiDatabase
import com.lumisound.android.library.AudioPermission
import com.lumisound.android.playback.PlayerController
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.isActive
import kotlinx.coroutines.launch
import kotlinx.coroutines.withTimeoutOrNull

/**
 * A periodic structured readout of live app state -- device, account, library,
 * playback, HTTP health and log counts -- sent to the bridge's event log every five
 * minutes, plus once at launch and once on return to the foreground.
 *
 * This exists for the same reason Lumisound's own version does: the app is tested
 * on a device the developer cannot attach a debugger to, so every investigation
 * otherwise starts by shipping a build that adds the logging it should already have
 * had. A standing "what does a real session actually look like" sample means the
 * next investigation starts with data in hand.
 *
 * The foreground catch-up is not an afterthought: on iOS the periodic tick
 * effectively never fired, because a repeating timer does not run while the app is
 * suspended and the only snapshots that ever landed were launch-time ones. A tick
 * that is overdue when the app comes back is taken then, which costs nothing and
 * closes exactly that gap.
 */
class DiagnosticsSnapshotService(
    private val context: Context,
    private val remote: RemoteLogger,
    private val account: StateFlow<AccountState>,
    private val tokenStore: TokenStore,
    private val config: BridgeConfig,
    private val database: LumiDatabase,
    private val player: PlayerController,
    private val httpMetrics: HttpMetrics,
    private val scope: CoroutineScope,
) {

    private val _lastSnapshot = MutableStateFlow<Map<String, Any?>>(emptyMap())

    /** The most recent snapshot, so the in-app Diagnostics screen shows the same data. */
    val lastSnapshot: StateFlow<Map<String, Any?>> = _lastSnapshot.asStateFlow()

    @Volatile
    private var lastSentAt: Long = 0

    fun start() {
        scope.launch {
            // Wait for the session to resolve before the launch sample. Without this the
            // first snapshot of every run reported `signedIn: false` while a valid
            // 30-day session was being restored two hundred milliseconds later -- a
            // telemetry artefact that reads exactly like the bug it is not.
            withTimeoutOrNull(SESSION_RESOLVE_TIMEOUT_MS) {
                account.first { it != AccountState.Unknown }
            }
            send("app_launch")
            while (isActive) {
                delay(INTERVAL_MS)
                send("periodic")
            }
        }
    }

    /** Called when the UI returns to the foreground; only fires if a tick is overdue. */
    fun noteForeground() {
        if (System.currentTimeMillis() - lastSentAt < INTERVAL_MS) return
        scope.launch { send("foreground") }
    }

    /** An explicit "send it now" from the Diagnostics screen. */
    fun sendNow(reason: String = "manual") {
        scope.launch { send(reason) }
    }

    private suspend fun send(reason: String) {
        lastSentAt = System.currentTimeMillis()
        val detail = try {
            buildSnapshot()
        } catch (e: Exception) {
            mapOf("snapshotError" to "${e.javaClass.simpleName}: ${e.message}")
        }
        _lastSnapshot.value = detail
        remote.log(
            category = "diagnostics",
            event = "snapshot",
            message = reason,
            detail = detail,
        )
    }

    private suspend fun buildSnapshot(): Map<String, Any?> = mapOf(
        "reasonAt" to System.currentTimeMillis(),
        "device" to deviceSnapshot(),
        "account" to accountSnapshot(),
        "library" to librarySnapshot(),
        "playback" to playbackSnapshot(),
        "http" to httpSnapshot(),
        "logs" to mapOf(
            "errorCount" to AppLogger.errorCount(),
            "counts" to AppLogger.counts(),
        ),
    )

    private fun deviceSnapshot(): Map<String, Any?> {
        val runtime = Runtime.getRuntime()
        val result = mutableMapOf<String, Any?>(
            "appVersion" to BuildConfig.VERSION_NAME,
            "versionCode" to BuildConfig.VERSION_CODE,
            "buildType" to if (BuildConfig.DEBUG) "debug" else "release",
            "manufacturer" to Build.MANUFACTURER,
            "model" to Build.MODEL,
            "api" to Build.VERSION.SDK_INT,
            "release" to Build.VERSION.RELEASE,
            // Heap headroom, not system memory: an OOM kill on a big library scan
            // shows up here first as the used figure closing on the max.
            "heapUsedMb" to (runtime.totalMemory() - runtime.freeMemory()) / 1_048_576,
            "heapMaxMb" to runtime.maxMemory() / 1_048_576,
        )
        try {
            val activityManager = context.getSystemService(Context.ACTIVITY_SERVICE) as ActivityManager
            val memory = ActivityManager.MemoryInfo().also { activityManager.getMemoryInfo(it) }
            result["systemLowMemory"] = memory.lowMemory
            result["systemAvailMb"] = memory.availMem / 1_048_576
            result["isBackgroundRestricted"] = activityManager.isBackgroundRestricted
        } catch (e: Exception) {
            result["memoryError"] = e.javaClass.simpleName
        }
        try {
            val power = context.getSystemService(Context.POWER_SERVICE) as PowerManager
            result["powerSaveMode"] = power.isPowerSaveMode
            // Thermal throttling is the usual explanation for "it got slow after a
            // while", and it is invisible from inside the app unless asked for.
            result["thermalStatus"] = power.currentThermalStatus
            result["ignoringBatteryOptimizations"] =
                power.isIgnoringBatteryOptimizations(context.packageName)
        } catch (e: Exception) {
            result["powerError"] = e.javaClass.simpleName
        }
        try {
            val battery = context.getSystemService(Context.BATTERY_SERVICE) as BatteryManager
            result["batteryPercent"] = battery.getIntProperty(BatteryManager.BATTERY_PROPERTY_CAPACITY)
        } catch (e: Exception) {
            result["batteryError"] = e.javaClass.simpleName
        }
        return result
    }

    private fun accountSnapshot(): Map<String, Any?> {
        val state = account.value
        return mapOf(
            "signedIn" to (state is AccountState.SignedIn),
            // Whether a credential exists at all, separately from whether it has been
            // validated yet: the two differ during startup and after a network failure,
            // and conflating them hides which of those is happening.
            "tokenPresent" to (tokenStore.token != null),
            "sessionResolved" to (state != AccountState.Unknown),
            "userId" to tokenStore.userId,
            "username" to (state as? AccountState.SignedIn)?.user?.username,
            "bridgeIsOfficial" to config.isOfficial,
            // Whether a shared key is even present: its absence silently 401s every
            // legacy route, which presents as search and streaming "not working".
            "hasSharedKey" to config.sharedApiKey.isNotEmpty(),
        )
    }

    private suspend fun librarySnapshot(): Map<String, Any?> = mapOf(
        "cloudTracks" to database.cloudTracks().count(),
        "favorites" to database.favorites().count(),
        "playlists" to database.playlists().count(),
        "historyRows" to database.history().count(),
        "localTracks" to database.localTracks().count(),
        "downloads" to database.downloads().count(),
        "downloadBytes" to database.downloads().totalBytes(),
        // The permission the device actually wants, and whether it is held -- the first
        // real session lost its whole device library to this and the snapshot could not
        // say so.
        "audioPermission" to AudioPermission.required.substringAfterLast('.'),
        "audioPermissionGranted" to AudioPermission.isGranted(context),
    )

    private fun playbackSnapshot(): Map<String, Any?> {
        val state = player.state.value
        return mapOf(
            "controllerConnected" to state.connected,
            "isPlaying" to state.isPlaying,
            "hasQueue" to state.hasQueue,
            "positionMs" to state.positionMs,
            "durationMs" to state.durationMs,
            // The track's path, not its title: a path says whether it was a locked
            // file, which folder it came from, and matches what the server logs.
            "currentServerPath" to state.serverPath,
        )
    }

    private fun httpSnapshot(): Map<String, Any?> {
        val stats = httpMetrics.snapshot()
        return mapOf(
            "routes" to stats.mapValues { (_, stat) ->
                mapOf(
                    "calls" to stat.calls,
                    "failures" to stat.failures,
                    "lastStatus" to stat.lastStatus,
                    "avgMs" to stat.averageMs,
                    "slowestMs" to stat.slowestMs,
                )
            },
            "failingRoutes" to httpMetrics.failingRoutes().keys.toList(),
        )
    }

    private companion object {
        const val INTERVAL_MS = 5 * 60 * 1_000L
        const val SESSION_RESOLVE_TIMEOUT_MS = 8_000L
    }
}

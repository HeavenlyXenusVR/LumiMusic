package com.lumisound.android.diagnostics

import android.content.Context
import android.os.Build
import com.google.gson.Gson
import com.lumisound.android.BuildConfig
import com.lumisound.android.bridge.BridgeHttp
import com.lumisound.android.bridge.TokenStore
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.delay
import kotlinx.coroutines.isActive
import kotlinx.coroutines.launch
import okhttp3.MediaType.Companion.toMediaType
import okhttp3.RequestBody.Companion.toRequestBody
import java.io.File

/**
 * Flushes [AppLogger]'s ring to `POST /internal/logs` every 30 seconds, in
 * batches of at most 100 -- the server's own per-request cap.
 *
 * Every batch carries the device model, OS version, app version and (when signed
 * in) the user id, because the server stores those per row and a log line without
 * them cannot answer the first question anyone asks of it: which build, on what,
 * for whom.
 *
 * A failed flush is requeued rather than dropped, and anything still unsent when
 * the process dies is written to disk so the next launch can deliver it -- which
 * is the only way a crash's own last log lines ever reach the server.
 */
class TelemetryUploader(
    context: Context,
    private val http: BridgeHttp,
    private val tokenStore: TokenStore,
    private val gson: Gson,
    private val scope: CoroutineScope,
) {

    private val spoolFile = File(context.filesDir, "pending_telemetry.json")
    private val deviceModel = "${Build.MANUFACTURER} ${Build.MODEL}".take(50)
    private val osVersion = "Android ${Build.VERSION.RELEASE} (API ${Build.VERSION.SDK_INT})".take(20)
    private val appVersion = "${BuildConfig.VERSION_NAME} (${BuildConfig.VERSION_CODE})".take(20)

    fun start() {
        scope.launch {
            deliverSpool()
            while (isActive) {
                delay(FLUSH_INTERVAL_MS)
                flush()
            }
        }
    }

    /** Sends whatever is buffered right now. Safe to call from anywhere. */
    fun flushNow() {
        scope.launch { flush() }
    }

    private suspend fun flush() {
        val batch = AppLogger.drain(MAX_BATCH)
        if (batch.isEmpty()) return
        if (!send(batch)) {
            AppLogger.requeue(batch)
            spool(batch)
        }
    }

    private suspend fun send(batch: List<LogEntry>): Boolean = try {
        val payload = batch.map { entry ->
            buildMap<String, Any?> {
                put("level", entry.level)
                put("category", entry.category)
                put("message", entry.message)
                put("timestamp", entry.timestamp)
                put("extra", entry.extra)
                put("deviceModel", deviceModel)
                put("osVersion", osVersion)
                put("appVersion", appVersion)
                tokenStore.userId?.let { put("userId", it) }
            }
        }
        http.diagnostics.uploadLogs(
            gson.toJson(payload).toRequestBody("application/json".toMediaType())
        )
        true
    } catch (e: Exception) {
        false
    }

    /**
     * Persists a batch that could not be sent. Overwrites rather than appends, and
     * keeps only the most recent entries: an offline week must not leave a
     * multi-megabyte file behind that the next launch then tries to post in one
     * request the server would reject as too large.
     */
    private fun spool(batch: List<LogEntry>) {
        try {
            spoolFile.writeText(gson.toJson(batch.takeLast(MAX_BATCH)))
        } catch (e: Exception) {
            // Nothing to do: the entries stay in memory for the next flush.
        }
    }

    /** Called once at launch -- this is how a crash's final log lines get out. */
    private suspend fun deliverSpool() {
        val text = try {
            if (!spoolFile.exists()) return
            spoolFile.readText()
        } catch (e: Exception) {
            return
        }
        val entries = try {
            gson.fromJson(text, Array<LogEntry>::class.java)?.toList().orEmpty()
        } catch (e: Exception) {
            emptyList()
        }
        if (entries.isEmpty()) {
            spoolFile.delete()
            return
        }
        if (send(entries)) {
            spoolFile.delete()
            AppLogger.i("telemetry", "delivered ${entries.size} spooled entries from a previous run")
        }
    }

    /** Writes the in-memory ring to the spool. Called from the crash handler. */
    fun spoolEverythingSynchronously() {
        val entries = AppLogger.snapshot(MAX_BATCH)
        if (entries.isNotEmpty()) spool(entries)
    }

    private companion object {
        const val FLUSH_INTERVAL_MS = 30_000L
        const val MAX_BATCH = 100
    }
}

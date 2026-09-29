package com.lumisound.android.gallery

import android.content.Context
import coil3.SingletonImageLoader
import coil3.request.CachePolicy
import coil3.request.ImageRequest
import com.google.gson.Gson
import com.google.gson.reflect.TypeToken
import com.lumisound.android.bridge.BridgeConfig
import com.lumisound.android.bridge.BridgeHttp
import com.lumisound.android.bridge.TokenStore
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock

/** What the gallery settings screen and the backdrop read. */
data class GalleryState(
    /** The settings in effect: Lumisound's while [followLumisound], this phone's otherwise. */
    val settings: GallerySettings = GallerySettings(),
    /** Lumisound's settings as last pulled, kept so following can be switched back on. */
    val lumisoundSettings: GallerySettings? = null,
    val followLumisound: Boolean = true,
    val photos: List<GalleryPhoto> = emptyList(),
    val syncing: Boolean = false,
    val lastSyncedAt: Long? = null,
    val error: String? = null,
) {
    /** True when there is something to draw. */
    val showing: Boolean get() = settings.enabled && photos.isNotEmpty()
}

/**
 * The account's Gallery Background, imported from Lumisound on iPhone and applied here.
 *
 * Photos and settings are pulled on launch and every time the app comes to the
 * foreground (see [syncIfStale]). New photos are fetched into the image disk cache as
 * they arrive, so the slideshow never waits on the network. The last pulled list is kept
 * so the background is up at once on the next launch, offline included.
 *
 * Settings follow the iPhone until they are changed here. Changing any of them switches
 * this phone to its own copy; [setFollowLumisound] switches back.
 */
class GalleryBackgroundStore(
    private val context: Context,
    private val http: BridgeHttp,
    private val config: BridgeConfig,
    private val tokenStore: TokenStore,
    private val gson: Gson = Gson(),
    private val clock: () -> Long = System::currentTimeMillis,
) {
    private val prefs = context.getSharedPreferences("lumimusic_gallery", Context.MODE_PRIVATE)
    private val syncLock = Mutex()

    private val _state = MutableStateFlow(load())
    val state: StateFlow<GalleryState> = _state.asStateFlow()

    /** Pulls unless the last pull was under [minIntervalMs] ago. */
    suspend fun syncIfStale(minIntervalMs: Long = STALE_AFTER_MS) {
        val last = _state.value.lastSyncedAt ?: 0L
        if (clock() - last < minIntervalMs) return
        sync()
    }

    /** Pulls the photo list and Lumisound's settings, and applies both. */
    suspend fun sync() {
        if (tokenStore.token == null) return
        syncLock.withLock {
            _state.update { it.copy(syncing = true, error = null) }
            try {
                val api = http.gallery
                val photos = GalleryMapping.photos(api.images(), config.baseUrl)
                // Settings are best-effort: a snapshot failure should not hold back new photos.
                val lumisound = try {
                    GalleryMapping.fromLumisound(api.syncSnapshot(), _state.value.lumisoundSettings ?: GallerySettings())
                } catch (e: CancellationException) {
                    throw e
                } catch (e: Exception) {
                    _state.value.lumisoundSettings
                }
                val previous = _state.value.photos.mapTo(HashSet()) { it.id }
                _state.update {
                    it.copy(
                        photos = photos,
                        lumisoundSettings = lumisound,
                        settings = if (it.followLumisound && lumisound != null) lumisound else it.settings,
                        syncing = false,
                        lastSyncedAt = clock(),
                    )
                }
                persist()
                prefetch(photos.filter { it.id !in previous })
            } catch (e: CancellationException) {
                _state.update { it.copy(syncing = false) }
                throw e
            } catch (e: Exception) {
                _state.update { it.copy(syncing = false, error = e.message ?: "Couldn't reach the bridge") }
            }
        }
    }

    /** A change made on this phone; it stops following Lumisound's settings. */
    fun updateSettings(change: (GallerySettings) -> GallerySettings) {
        _state.update { it.copy(settings = change(it.settings).normalised(), followLumisound = false) }
        persist()
    }

    fun setFollowLumisound(follow: Boolean) {
        _state.update {
            it.copy(
                followLumisound = follow,
                settings = if (follow) it.lumisoundSettings ?: it.settings else it.settings,
            )
        }
        persist()
    }

    /** Sign-out: the next account's photos must never appear behind this one's. */
    fun clear() {
        _state.value = GalleryState()
        prefs.edit().clear().apply()
    }

    private fun prefetch(photos: List<GalleryPhoto>) {
        if (photos.isEmpty()) return
        val loader = SingletonImageLoader.get(context)
        photos.forEach { photo ->
            loader.enqueue(
                ImageRequest.Builder(context)
                    .data(photo.url)
                    .size(PREFETCH_SIZE_PX)
                    .memoryCachePolicy(CachePolicy.DISABLED)
                    .build()
            )
        }
    }

    private fun persist() {
        val s = _state.value
        prefs.edit()
            .putString(KEY_SETTINGS, gson.toJson(StoredSettings.from(s.settings)))
            .putString(KEY_LUMISOUND, s.lumisoundSettings?.let { gson.toJson(StoredSettings.from(it)) })
            .putBoolean(KEY_FOLLOW, s.followLumisound)
            .putString(KEY_PHOTOS, gson.toJson(s.photos))
            .putLong(KEY_SYNCED_AT, s.lastSyncedAt ?: 0L)
            .apply()
    }

    private fun load(): GalleryState {
        fun settings(key: String) = prefs.getString(key, null)?.let {
            runCatching { gson.fromJson(it, StoredSettings::class.java)?.toSettings() }.getOrNull()
        }
        val photos = prefs.getString(KEY_PHOTOS, null)?.let {
            runCatching { gson.fromJson<List<GalleryPhoto>>(it, object : TypeToken<List<GalleryPhoto>>() {}.type) }.getOrNull()
        }.orEmpty()
        return GalleryState(
            settings = settings(KEY_SETTINGS) ?: GallerySettings(),
            lumisoundSettings = settings(KEY_LUMISOUND),
            followLumisound = prefs.getBoolean(KEY_FOLLOW, true),
            photos = photos,
            lastSyncedAt = prefs.getLong(KEY_SYNCED_AT, 0L).takeIf { it > 0L },
        )
    }

    /** Settings as stored: the transition by name, so a renamed enum entry falls back to Fade. */
    private data class StoredSettings(
        val enabled: Boolean = false,
        val opacity: Float = GallerySettings.DEFAULT_OPACITY,
        val blurRadius: Float = GallerySettings.DEFAULT_BLUR,
        val intervalSeconds: Int = GallerySettings.DEFAULT_INTERVAL,
        val transition: String? = null,
        val kenBurns: Boolean = false,
    ) {
        fun toSettings() = GallerySettings(
            enabled = enabled,
            opacity = opacity,
            blurRadius = blurRadius,
            intervalSeconds = intervalSeconds,
            transition = GalleryTransition.entries.firstOrNull { it.name == transition } ?: GalleryTransition.Fade,
            kenBurns = kenBurns,
        ).normalised()

        companion object {
            fun from(s: GallerySettings) =
                StoredSettings(s.enabled, s.opacity, s.blurRadius, s.intervalSeconds, s.transition.name, s.kenBurns)
        }
    }

    private companion object {
        const val KEY_SETTINGS = "settings"
        const val KEY_LUMISOUND = "lumisound_settings"
        const val KEY_FOLLOW = "follow_lumisound"
        const val KEY_PHOTOS = "photos"
        const val KEY_SYNCED_AT = "synced_at"
        const val STALE_AFTER_MS = 60_000L
        const val PREFETCH_SIZE_PX = 1440
    }
}

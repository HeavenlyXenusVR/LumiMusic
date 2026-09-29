package com.lumisound.android.social

import com.lumisound.android.bridge.BridgeHttp
import com.lumisound.android.bridge.TokenStore
import com.lumisound.android.bridge.model.PresenceUpdate
import com.lumisound.android.diagnostics.AppLogger
import com.lumisound.android.playback.PlayerController
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.launch

/**
 * Tells friends whether this account is online and what it is playing, through the same
 * `POST /api/social/presence` heartbeat Lumisound sends.
 *
 * Runs only while the app is in the foreground -- the bridge treats a heartbeat older than
 * its freshness window as offline anyway -- plus one `going_offline` beat on the way to the
 * background, so friends see the change at once rather than a couple of minutes later.
 * Whether the track itself is shown is the friend-facing profile's own share-now-playing
 * toggle, applied server-side; this always reports honestly and lets the server decide.
 */
class PresenceService(
    private val http: BridgeHttp,
    private val tokenStore: TokenStore,
    private val player: PlayerController,
    private val scope: CoroutineScope,
) {

    private var loop: Job? = null
    private var trackWatch: Job? = null

    fun onForeground() {
        if (loop?.isActive == true) return
        loop = scope.launch {
            while (true) {
                beat(goingOffline = false)
                delay(HEARTBEAT_MS)
            }
        }
        // A track change is news worth sending now rather than at the next tick.
        trackWatch = scope.launch {
            player.state
                .map { it.mediaId to it.isPlaying }
                .distinctUntilChanged()
                .collect { beat(goingOffline = false) }
        }
    }

    fun onBackground() {
        loop?.cancel()
        trackWatch?.cancel()
        loop = null
        trackWatch = null
        // Still playing in the background is still listening; only an idle app goes offline.
        scope.launch { beat(goingOffline = !player.state.value.isPlaying) }
    }

    private suspend fun beat(goingOffline: Boolean) {
        if (tokenStore.token == null) return
        val state = player.state.value
        val playing = state.isPlaying && !goingOffline
        try {
            http.social.updatePresence(
                PresenceUpdate(
                    isPlaying = playing,
                    nowPlayingTitle = state.title.takeIf { playing },
                    nowPlayingArtist = state.artist.takeIf { playing },
                    // Only a public URL: a cloud track's artwork is JWT-gated and a device
                    // file's is a content:// URI, both a broken image on a friend's screen.
                    nowPlayingArtworkUrl = state.artworkUrl?.takeIf {
                        playing && it.startsWith("https://") && "/user/music/" !in it
                    },
                    goingOffline = goingOffline,
                )
            )
        } catch (e: Exception) {
            AppLogger.w("social", "presence heartbeat failed: ${e.javaClass.simpleName}")
        }
    }

    private companion object {
        const val HEARTBEAT_MS = 45_000L
    }
}

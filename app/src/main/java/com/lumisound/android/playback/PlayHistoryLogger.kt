package com.lumisound.android.playback

import android.util.Log
import com.lumisound.android.bridge.BridgeHttp
import com.lumisound.android.bridge.TokenStore
import com.lumisound.android.bridge.model.LogPlayRequest
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.distinctUntilChangedBy
import kotlinx.coroutines.launch

/**
 * Logs a play to `POST /user/history` about five seconds after a track starts --
 * the same accidental-skip filter Lumisound applies, so a scrub through ten tracks
 * doesn't register as ten plays.
 *
 * This one call is the trigger for several server-side features at once
 * (scrobbling to any linked Last.fm/Libre.fm/ListenBrainz account, the Discord
 * "now playing" webhook, and the stats/achievements aggregates), which is why
 * listening on Android counts toward exactly the same account totals as listening
 * on iOS with no extra client-side work.
 */
class PlayHistoryLogger(
    private val http: BridgeHttp,
    private val tokenStore: TokenStore,
    private val player: PlayerController,
    private val scope: CoroutineScope,
) {

    private var pending: Job? = null

    fun start() {
        scope.launch {
            player.state
                // Keyed on the queue entry, not the server path: a streamed track or a
                // device file has no server path, and keying on it made every one of them
                // look like "no change" -- so none of them were ever logged.
                .distinctUntilChangedBy { it.mediaId }
                .collect { state -> schedule(state) }
        }
    }

    private fun schedule(state: PlaybackUiState) {
        pending?.cancel()
        val title = state.title ?: return
        if (tokenStore.token == null) return
        // An episode is tracked as progress on the podcast routes, not as a music play:
        // counting it here would put a show in top artists and scrobble it to Last.fm.
        if (state.podcastFeedUrl != null) return
        pending = scope.launch {
            delay(SKIP_FILTER_MS)
            try {
                http.libraryData.logPlay(
                    LogPlayRequest(
                        title = title,
                        artist = state.artist,
                        trackUrl = state.trackUrl,
                        localSongId = state.serverPath,
                        listenSeconds = (SKIP_FILTER_MS / 1000).toInt(),
                    )
                )
            } catch (e: Exception) {
                // Fire-and-forget: a missed history row is not worth interrupting
                // playback or showing an error over.
                Log.w(TAG, "history log failed: ${e.javaClass.simpleName}")
            }
        }
    }

    private companion object {
        const val TAG = "LumiHistory"
        const val SKIP_FILTER_MS = 5_000L
    }
}

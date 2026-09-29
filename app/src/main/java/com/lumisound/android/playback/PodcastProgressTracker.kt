package com.lumisound.android.playback

import com.lumisound.android.bridge.BridgeHttp
import com.lumisound.android.bridge.TokenStore
import com.lumisound.android.bridge.model.EpisodeProgressRequest
import com.lumisound.android.diagnostics.AppLogger
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.launch

/**
 * Saves the position of whatever podcast episode is playing to
 * `PUT /user/podcasts/episode-progress` -- the same row Lumisound reads, so an episode
 * paused on the phone resumes here at the same second and vice versa.
 *
 * Written on a pause, when the episode changes, and every [SAVE_EVERY_MS] while playing:
 * often enough that a killed process loses under half a minute, rarely enough that an
 * hour-long episode costs a hundred small writes rather than thousands.
 */
class PodcastProgressTracker(
    private val http: BridgeHttp,
    private val tokenStore: TokenStore,
    private val player: PlayerController,
    private val scope: CoroutineScope,
) {

    private var last: PlaybackUiState? = null
    private var lastSavedAt = 0L

    fun start() {
        scope.launch {
            player.state.collect { state -> onState(state) }
        }
    }

    private fun onState(state: PlaybackUiState) {
        val previous = last
        last = state
        if (previous?.episodeGuid != null && previous.episodeGuid != state.episodeGuid) {
            // Switched away: the previous episode's final position is the one that matters.
            save(previous)
        }
        if (state.episodeGuid == null) return
        val now = System.currentTimeMillis()
        val paused = previous?.isPlaying == true && !state.isPlaying
        if (paused || (state.isPlaying && now - lastSavedAt >= SAVE_EVERY_MS)) save(state)
    }

    private fun save(state: PlaybackUiState) {
        val feed = state.podcastFeedUrl ?: return
        val guid = state.episodeGuid ?: return
        if (tokenStore.token == null || state.positionMs < 5_000) return
        lastSavedAt = System.currentTimeMillis()
        val body = EpisodeProgressRequest(
            feedUrl = feed,
            episodeGuid = guid,
            title = state.title,
            positionSeconds = state.positionMs / 1000.0,
            durationSeconds = state.durationMs / 1000.0,
            completed = isCompleted(state.positionMs, state.durationMs),
        )
        scope.launch {
            try {
                http.podcasts.saveProgress(body)
            } catch (e: Exception) {
                AppLogger.w("podcasts", "progress save failed: ${e.javaClass.simpleName}")
            }
        }
    }

    companion object {
        const val SAVE_EVERY_MS = 20_000L

        /**
         * Within the last 30 seconds counts as finished: outros and trailing ads mean
         * hardly anyone reaches the literal end, and an episode stuck at 99% forever sits
         * in Continue Listening for good.
         */
        fun isCompleted(positionMs: Long, durationMs: Long): Boolean =
            durationMs > 0 && positionMs >= durationMs - 30_000
    }
}

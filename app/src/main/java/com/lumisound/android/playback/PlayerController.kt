package com.lumisound.android.playback

import android.content.ComponentName
import android.content.Context
import android.os.Bundle
import android.util.Log
import androidx.annotation.OptIn
import androidx.media3.common.MediaItem
import androidx.media3.common.MediaMetadata
import androidx.media3.common.Player
import androidx.media3.common.util.UnstableApi
import androidx.media3.session.MediaController
import androidx.media3.session.SessionToken
import com.google.common.util.concurrent.MoreExecutors
import com.lumisound.android.bridge.BridgeConfig
import com.lumisound.android.bridge.BridgeUrls
import com.lumisound.android.data.db.CloudTrackEntity
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch

/** What the UI needs to render a mini player and a Now Playing screen. */
data class PlaybackUiState(
    val connected: Boolean = false,
    val title: String? = null,
    val artist: String? = null,
    val artworkUrl: String? = null,
    val serverPath: String? = null,
    val isPlaying: Boolean = false,
    val positionMs: Long = 0,
    val durationMs: Long = 0,
    val hasQueue: Boolean = false,
)

/**
 * The app's handle on [PlaybackService]. Everything the UI does to playback goes
 * through a `MediaController`, never a player instance of its own, so there is
 * exactly one player in the process and the notification, Bluetooth controls and
 * the in-app UI can never disagree about what is playing.
 */
@OptIn(UnstableApi::class)
class PlayerController(
    private val context: Context,
    private val config: BridgeConfig,
    private val scope: CoroutineScope,
) {

    private val _state = MutableStateFlow(PlaybackUiState())
    val state: StateFlow<PlaybackUiState> = _state.asStateFlow()

    private var controller: MediaController? = null

    private val listener = object : Player.Listener {
        override fun onEvents(player: Player, events: Player.Events) = publish()
    }

    fun connect() {
        if (controller != null) return
        val token = SessionToken(context, ComponentName(context, PlaybackService::class.java))
        val future = MediaController.Builder(context, token).buildAsync()
        future.addListener({
            controller = try {
                future.get().also { it.addListener(listener) }
            } catch (e: Exception) {
                Log.w(TAG, "could not bind playback service: ${e.javaClass.simpleName}")
                null
            }
            publish()
            startTicking()
        }, MoreExecutors.directExecutor())
    }

    fun release() {
        controller?.removeListener(listener)
        controller?.release()
        controller = null
    }

    /** Plays [tracks] starting at [startIndex], replacing whatever queue exists. */
    fun play(tracks: List<CloudTrackEntity>, startIndex: Int) {
        val player = controller ?: return
        if (tracks.isEmpty()) return
        player.setMediaItems(tracks.map { it.toMediaItem(config.baseUrl) }, startIndex, 0L)
        player.prepare()
        player.play()
    }

    fun togglePlayPause() {
        val player = controller ?: return
        if (player.isPlaying) player.pause() else player.play()
    }

    fun next() = controller?.seekToNextMediaItem()

    fun previous() = controller?.seekToPreviousMediaItem()

    fun seekTo(positionMs: Long) = controller?.seekTo(positionMs)

    private fun startTicking() {
        // The controller only reports position on events, so a visible seek bar needs
        // its own tick. Half a second matches Lumisound's own position timer.
        scope.launch(Dispatchers.Main) {
            while (controller != null) {
                publish()
                kotlinx.coroutines.delay(500)
            }
        }
    }

    private fun publish() {
        val player = controller
        if (player == null) {
            _state.value = PlaybackUiState()
            return
        }
        val item = player.currentMediaItem
        val metadata = item?.mediaMetadata
        _state.value = PlaybackUiState(
            connected = true,
            title = metadata?.title?.toString(),
            artist = metadata?.artist?.toString(),
            artworkUrl = metadata?.artworkUri?.toString(),
            serverPath = item?.localConfiguration?.uri?.getQueryParameter("path"),
            isPlaying = player.isPlaying,
            positionMs = player.currentPosition.coerceAtLeast(0),
            durationMs = player.duration.takeIf { it > 0 } ?: 0,
            hasQueue = player.mediaItemCount > 0,
        )
    }

    private companion object {
        const val TAG = "LumiPlayer"
    }
}

/**
 * Cloud track -> playable item. The `serverPath` travels in the URL's `path`
 * query (which is also how the loudness gain and the locked-file handling find
 * their way back to the right track) and again in the metadata extras, so a
 * queue restored by the session can still be identified.
 */
@OptIn(UnstableApi::class)
fun CloudTrackEntity.toMediaItem(baseUrl: String): MediaItem {
    val streamUrl = BridgeUrls.stream(baseUrl, serverPath, isLocked)
    val artwork = if (hasArtwork) BridgeUrls.artwork(baseUrl, serverPath) else null
    return MediaItem.Builder()
        .setMediaId(serverPath)
        .setUri(streamUrl)
        .setMediaMetadata(
            MediaMetadata.Builder()
                .setTitle(title)
                .setArtist(artist)
                .setAlbumTitle(album)
                .setGenre(genre)
                .setArtworkUri(artwork?.let { android.net.Uri.parse(it) })
                .setIsPlayable(true)
                .setIsBrowsable(false)
                .setExtras(Bundle().apply {
                    putString(EXTRA_SERVER_PATH, serverPath)
                    putBoolean(EXTRA_IS_LOCKED, isLocked)
                })
                .build()
        )
        .build()
}

const val EXTRA_SERVER_PATH = "lumi.serverPath"
const val EXTRA_IS_LOCKED = "lumi.isLocked"

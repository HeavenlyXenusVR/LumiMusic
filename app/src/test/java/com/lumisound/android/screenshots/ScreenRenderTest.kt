package com.lumisound.android.screenshots

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import com.github.takahirom.roborazzi.captureRoboImage
import com.lumisound.android.playback.PlaybackUiState
import com.lumisound.android.ui.screens.nowplaying.MiniPlayer
import com.lumisound.android.ui.screens.nowplaying.NowPlayingContent
import com.lumisound.android.ui.theme.LocalLumiPalette
import com.lumisound.android.ui.theme.LumiMusicTheme
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config
import org.robolectric.annotation.GraphicsMode

/**
 * Whole screens, rendered from invented state.
 *
 * The states worth rendering are the awkward ones: a track whose duration the player never
 * worked out, a track with no artwork (which is most of this library), a queue position
 * deep in a few thousand tracks. Reaching those by hand on a phone takes a build, an
 * install and some luck; here they are four lines each.
 */
@RunWith(RobolectricTestRunner::class)
@GraphicsMode(GraphicsMode.Mode.NATIVE)
@Config(
    sdk = [34],
    application = android.app.Application::class,
    qualifiers = "w411dp-h891dp-night-xxhdpi",
)
class ScreenRenderTest {

    private fun capture(name: String, content: @Composable () -> Unit) {
        captureRoboImage(filePath = "build/outputs/roborazzi/$name.png") {
            LumiMusicTheme(accentHex = "#22D3EE") {
                Box(Modifier.fillMaxWidth().background(LocalLumiPalette.current.pageBrush)) { content() }
            }
        }
    }

    private val playing = PlaybackUiState(
        connected = true,
        title = "( Official Instrumental ) This is Our Big Night",
        artist = "( Official Instrumental ) This is Our Big Night",
        artworkUrl = null,
        serverPath = "(Mario) The Music Box Remastered OST - Title 1.opus.lms",
        isPlaying = true,
        positionMs = 71_000,
        durationMs = 126_000,
        hasQueue = true,
        queueSize = 3545,
        queueIndex = 0,
        shuffleEnabled = true,
        repeatMode = 2,
        speed = 1f,
    )

    @Test
    fun nowPlaying() = capture("screen-now-playing") {
        NowPlayingContent(
            state = playing,
            onSeek = {}, onToggle = {}, onPrevious = {}, onNext = {},
            onShuffle = {}, onRepeat = {}, onSpeed = {}, onOpenQueue = {},
        )
    }

    /** The case that shipped a full progress bar on an eleven-second-old track. */
    @Test
    fun nowPlayingWithUnknownDuration() = capture("screen-now-playing-unknown-duration") {
        NowPlayingContent(
            state = playing.copy(positionMs = 11_000, durationMs = 0, speed = 1.25f),
            onSeek = {}, onToggle = {}, onPrevious = {}, onNext = {},
            onShuffle = {}, onRepeat = {}, onSpeed = {}, onOpenQueue = {},
        )
    }

    @Test
    fun nowPlayingWithError() = capture("screen-now-playing-error") {
        NowPlayingContent(
            state = playing.copy(
                isPlaying = false,
                playbackError = "ERROR_CODE_IO_BAD_HTTP_STATUS: Response code: 401",
            ),
            onSeek = {}, onToggle = {}, onPrevious = {}, onNext = {},
            onShuffle = {}, onRepeat = {}, onSpeed = {}, onOpenQueue = {},
        )
    }

    @Test
    fun miniPlayer() = capture("screen-mini-player") {
        MiniPlayer(state = playing, onToggle = {}, onNext = {}, onExpand = {})
    }
}

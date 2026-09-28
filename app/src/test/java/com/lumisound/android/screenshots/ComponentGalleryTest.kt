package com.lumisound.android.screenshots

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.CloudQueue
import androidx.compose.material.icons.filled.Equalizer
import androidx.compose.material3.Button
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import com.github.takahirom.roborazzi.captureRoboImage
import com.lumisound.android.ui.components.EmptyState
import com.lumisound.android.ui.components.Hairline
import com.lumisound.android.ui.components.Pill
import com.lumisound.android.ui.components.SectionHeader
import com.lumisound.android.ui.components.SettingsGroup
import com.lumisound.android.ui.components.SettingsRow
import com.lumisound.android.ui.components.ShelfCard
import com.lumisound.android.ui.components.TrackRow
import com.lumisound.android.ui.theme.LocalLumiPalette
import com.lumisound.android.ui.theme.LumiMusicTheme
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config
import org.robolectric.annotation.GraphicsMode

/**
 * Renders the shared UI pieces to PNGs on the JVM.
 *
 * The point is not assertion, it is sight: this app is developed without a device or an
 * emulator attached, and four of the five defects in the first real screenshots -- a
 * progress bar pinned full on an unknown duration, rows printing their own title twice, a
 * backdrop that only existed for artwork almost no track has -- were plainly visible in a
 * picture and invisible in a log. These images are uploaded by CI so they can be looked at
 * before a build reaches a phone.
 *
 * Robolectric is pinned to SDK 34 rather than the project's compileSdk: it needs a
 * preinstrumented android-all jar for whatever level it runs, and those trail the newest
 * platform by a long way. Nothing here depends on a newer API.
 */
@RunWith(RobolectricTestRunner::class)
@GraphicsMode(GraphicsMode.Mode.NATIVE)
// The stock Application, not this app's: LumiMusicApp.onCreate builds the whole
// dependency graph -- encrypted storage, the player, the telemetry loop -- none of which
// a screenshot needs and none of which exists on the JVM.
@Config(
    sdk = [34],
    application = android.app.Application::class,
    qualifiers = "w411dp-h891dp-xxhdpi-night",
)
class ComponentGalleryTest {

    private fun capture(name: String, content: @Composable () -> Unit) {
        captureRoboImage(filePath = "build/outputs/roborazzi/$name.png") {
            LumiMusicTheme(accentHex = "#22D3EE") {
                Column(
                    Modifier
                        .fillMaxWidth()
                        .background(LocalLumiPalette.current.pageBrush)
                        .padding(vertical = 12.dp)
                ) { content() }
            }
        }
    }

    @Test
    fun trackRows() = capture("component-track-rows") {
        SectionHeader("All tracks", subtitle = "3545 in your cloud storage")
        TrackRow(
            title = "( Official Instrumental ) This is Our Big Night",
            subtitle = "Unknown artist",
            artworkModel = null,
            fallbackKey = "big-night.opus.lms",
            duration = "2:06",
            isLocked = true,
        )
        TrackRow(
            title = "Live and Learn",
            subtitle = "Crush 40 · Sonic Adventure 2",
            artworkModel = null,
            fallbackKey = "live-and-learn",
            duration = "3:32",
            isPlaying = true,
            isFavorite = true,
            isDownloaded = true,
        )
        TrackRow(
            title = "Windy and Ripply",
            subtitle = "Sonic Sound Archive",
            artworkModel = null,
            fallbackKey = "windy-and-ripply",
            duration = "1:58",
            onMenu = {},
        )
    }

    @Test
    fun shelves() = capture("component-shelf") {
        SectionHeader("Recently added", subtitle = "Newest uploads to your cloud storage")
        androidx.compose.foundation.lazy.LazyRow {
            items(count = 4) { index ->
                ShelfCard(
                    title = listOf("Title 1", "Title 2", "Title 3", "Title 4")[index],
                    subtitle = "Sonic Sound Archive",
                    artworkModel = null,
                    // Deliberately near-identical keys: this is the case that used to render
                    // four cards of the same colour.
                    fallbackKey = "(Mario) The Music Box OST - Title ${index + 1}.opus.lms",
                    onClick = {},
                )
            }
        }
    }

    @Test
    fun settingsGroup() = capture("component-settings") {
        SettingsGroup("Playback & storage") {
            SettingsRow(Icons.Filled.Equalizer, "Equalizer", "Your device's own bands and presets") {}
            Hairline()
            SettingsRow(Icons.Filled.CloudQueue, "Offline downloads", "12 tracks saved") {}
        }
        Spacer(Modifier.height(16.dp))
        Pill("3545 tracks", Modifier.padding(start = 20.dp))
    }

    @Test
    fun emptyState() = capture("component-empty") {
        EmptyState(
            icon = Icons.Filled.CloudQueue,
            title = "Audio found, but no music",
            message = "1 audio file is on this device, but Android does not classify it as music — " +
                "ringtones, notifications and podcasts are excluded. Your cloud library is unaffected.",
            action = { Button(onClick = {}) { Text("Scan again") } },
        )
    }
}

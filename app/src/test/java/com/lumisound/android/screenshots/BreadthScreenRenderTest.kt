package com.lumisound.android.screenshots

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import com.github.takahirom.roborazzi.captureRoboImage
import com.lumisound.android.bridge.model.AchievementsDto
import com.lumisound.android.bridge.model.CommunityTrackDto
import com.lumisound.android.bridge.model.DailyPickResponse
import com.lumisound.android.bridge.model.DayStatDto
import com.lumisound.android.bridge.model.EpisodeProgressDto
import com.lumisound.android.bridge.model.LifetimeStatsDto
import com.lumisound.android.bridge.model.StreamTrackDto
import com.lumisound.android.bridge.model.TopArtistDto
import com.lumisound.android.bridge.model.TopTrackDto
import com.lumisound.android.bridge.model.TwinDto
import com.lumisound.android.bridge.model.TwinResponse
import com.lumisound.android.bridge.model.WeeklyMixResponse
import com.lumisound.android.bridge.model.WeeklyMixTrackDto
import com.lumisound.android.lyrics.LrcParser
import com.lumisound.android.lyrics.LyricsResult
import com.lumisound.android.playback.PlaybackUiState
import com.lumisound.android.ui.components.Loadable
import com.lumisound.android.ui.screens.home.HomeCallbacks
import com.lumisound.android.ui.screens.home.HomeContent
import com.lumisound.android.ui.screens.home.HomeData
import com.lumisound.android.ui.screens.nowplaying.NowPlayingContent
import com.lumisound.android.ui.screens.search.SearchCallbacks
import com.lumisound.android.ui.screens.search.SearchContent
import com.lumisound.android.ui.screens.search.SearchUiState
import com.lumisound.android.ui.screens.stats.AchievementsContent
import com.lumisound.android.ui.screens.stats.RewindContent
import com.lumisound.android.ui.screens.stats.RewindPeriod
import com.lumisound.android.ui.screens.stats.StatsContent
import com.lumisound.android.ui.screens.stats.StatsData
import com.lumisound.android.ui.screens.stats.toSummary
import com.lumisound.android.ui.theme.LocalLumiPalette
import com.lumisound.android.ui.theme.LumiMusicTheme
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config
import org.robolectric.annotation.GraphicsMode
import java.time.LocalDate

/**
 * The milestone-3 screens, rendered from invented bridge responses -- the shapes the
 * real endpoints return, with none of the network in between.
 */
@RunWith(RobolectricTestRunner::class)
@GraphicsMode(GraphicsMode.Mode.NATIVE)
@Config(
    sdk = [34],
    application = android.app.Application::class,
    qualifiers = "w411dp-h891dp-night-xxhdpi",
)
class BreadthScreenRenderTest {

    private fun capture(name: String, content: @Composable () -> Unit) {
        captureRoboImage(filePath = "build/outputs/roborazzi/$name.png") {
            LumiMusicTheme(accentHex = "#EC4079") {
                Box(Modifier.fillMaxWidth().background(LocalLumiPalette.current.pageBrush)) { content() }
            }
        }
    }

    private fun stream(title: String, artist: String, seconds: Int = 214, source: String = "youtube") =
        StreamTrackDto(id = title.lowercase().replace(' ', '-'), title = title, artist = artist, durationSeconds = seconds, source = source)

    private val streams = listOf(
        stream("Midnight City", "M83", 244),
        stream("Tidal", "Lumen Drift", 198, source = "soundcloud"),
        stream("Glass Hours", "Paper Satellites", 305),
        stream("Northern Static", "Sable Coast", 187),
        stream("Coastline", "Neon Harbor", 229),
    )

    @Test
    fun home() = capture("screen-home") {
        HomeContent(
            data = HomeData(
                displayName = "Xenus",
                unreadNotifications = 3,
                dailyPick = Loadable.Ready(
                    DailyPickResponse(streams[0], "Big synths and a night-drive feel, like the Neon Harbor you've had on repeat.")
                ),
                weeklyMix = Loadable.Ready(
                    WeeklyMixResponse(
                        List(8) { i ->
                            WeeklyMixTrackDto(title = "Mix Track ${i + 1}", artist = "Artist ${i % 3}", relativePath = "mix/track$i.opus")
                        }
                    )
                ),
                discoverMix = Loadable.Ready(streams),
                continueListening = Loadable.Ready(
                    listOf(EpisodeProgressDto("g1", "https://feed.example/a", "Ep. 212 — The Loudness War", 1_260.0, 3_540.0))
                ),
                onThisDay = Loadable.Ready(emptyList()),
                recentlyPlayed = Loadable.Loading,
                trending = Loadable.Ready(
                    listOf(
                        CommunityTrackDto("Espresso", "Sabrina Carpenter", 41, 12),
                        CommunityTrackDto("Midnight City", "M83", 22, 7),
                        CommunityTrackDto("Tidal", "Lumen Drift", 9, 1),
                    )
                ),
                twin = Loadable.Ready(TwinResponse(TwinDto("aurora", "Aurora", null, 78, listOf("M83", "Tycho", "Bonobo")))),
            ),
            callbacks = HomeCallbacks(),
        )
    }

    @Test
    fun searchEmpty() = capture("screen-search-empty") {
        SearchContent(
            SearchUiState(
                recent = listOf("m83 midnight city", "lofi beats", "tycho"),
                trending = listOf("espresso", "taylor swift", "phonk", "nightcore", "sleep music"),
            ),
            SearchCallbacks(),
        )
    }

    @Test
    fun searchResults() = capture("screen-search-results") {
        SearchContent(
            SearchUiState(
                query = "night drive",
                submitted = "night drive",
                streamResults = Loadable.Ready(streams),
                playingId = "youtube:glass-hours",
            ),
            SearchCallbacks(),
        )
    }

    @Test
    fun searchFailed() = capture("screen-search-failed") {
        SearchContent(
            SearchUiState(query = "x", submitted = "x", streamResults = Loadable.Failed("Couldn't reach the server. Check your connection.")),
            SearchCallbacks(),
        )
    }

    private val today = LocalDate.of(2026, 9, 29)

    private fun daysBack(n: Int, plays: (Int) -> Int) = (0 until n).mapNotNull { i ->
        val count = plays(i)
        if (count == 0) null else DayStatDto(today.minusDays(i.toLong()).toString(), count, count * 200L)
    }

    private val lifetime = LifetimeStatsDto(
        totalPlays = 4_812,
        totalListenSeconds = 912_340,
        topArtists = listOf(TopArtistDto("M83", 310), TopArtistDto("Tycho", 204), TopArtistDto("Bonobo", 150)),
        topTracks = listOf(TopTrackDto("Midnight City", "M83", 88), TopTrackDto("Awake", "Tycho", 61)),
    )

    @Test
    fun stats() = capture("screen-stats") {
        StatsContent(
            data = StatsData(
                lifetime = Loadable.Ready(lifetime),
                achievements = Loadable.Ready(AchievementsDto(4_812, 912_340, 12, 41, listOf("plays_10"))),
                week = Loadable.Ready(daysBack(7) { i -> listOf(12, 0, 30, 8, 22, 5, 17)[i] }),
                heatmap = Loadable.Ready(daysBack(365) { i -> ((i * 7919) % 13).let { if (it < 4) 0 else it } }),
            ),
            today = today,
            onBack = {}, onRetry = {}, onPlay = { _, _ -> },
        )
    }

    @Test
    fun rewind() = capture("screen-rewind") {
        RewindContent(
            period = RewindPeriod.AllTime,
            summary = Loadable.Ready(lifetime.toSummary()),
            onPeriodChange = {}, onBack = {}, onRetry = {}, onShare = {},
        )
    }

    @Test
    fun achievements() = capture("screen-achievements") {
        AchievementsContent(
            Loadable.Ready(AchievementsDto(120, 30_000, 3, 9, listOf("plays_10", "plays_50", "plays_100", "hours_1", "streak_3", "streak_7", "night_owl"))),
            onBack = {}, onRetry = {},
        )
    }

    @Test
    fun nowPlayingLyrics() = capture("screen-now-playing-lyrics") {
        NowPlayingContent(
            state = PlaybackUiState(
                connected = true,
                title = "Midnight City",
                artist = "M83",
                isPlaying = true,
                positionMs = 19_000,
                durationMs = 244_000,
                hasQueue = true,
                queueSize = 5,
                sleepAtMs = System.currentTimeMillis() + 23 * 60_000,
            ),
            onSeek = {}, onToggle = {}, onPrevious = {}, onNext = {},
            onShuffle = {}, onRepeat = {}, onSpeed = {}, onOpenQueue = {},
            showLyrics = true,
            lyrics = LyricsResult.Synced(
                LrcParser.parse(
                    """
                    [00:05.00]Streetlights hum a borrowed tune
                    [00:09.00]Every window paints the road
                    [00:14.00]We drive until the static clears
                    [00:18.00]And the radio finds our song
                    [00:23.00]Hold the note a little longer
                    [00:27.00]Let the city fade to blue
                    """.trimIndent()
                )
            ),
        )
    }
}

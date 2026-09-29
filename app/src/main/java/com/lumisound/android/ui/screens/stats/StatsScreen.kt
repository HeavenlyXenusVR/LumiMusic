package com.lumisound.android.ui.screens.stats

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.BarChart
import androidx.compose.material.icons.filled.GridOn
import androidx.compose.material.icons.filled.LocalFireDepartment
import androidx.compose.material.icons.filled.Person
import androidx.compose.material.icons.filled.PlayCircle
import androidx.compose.material.icons.filled.Schedule
import androidx.compose.material.icons.filled.WorkspacePremium
import androidx.compose.material.icons.filled.MusicNote
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import com.lumisound.android.AppContainer
import com.lumisound.android.bridge.model.AchievementsDto
import com.lumisound.android.bridge.model.DayStatDto
import com.lumisound.android.bridge.model.LifetimeStatsDto
import com.lumisound.android.ui.components.IconSectionHeader
import com.lumisound.android.ui.components.Loadable
import com.lumisound.android.ui.components.LoadableSection
import com.lumisound.android.ui.components.rememberLoadable
import com.lumisound.android.ui.components.trackSubtitle
import com.lumisound.android.ui.searchAndPlay
import com.lumisound.android.ui.theme.SectionTint
import kotlinx.coroutines.launch
import java.time.LocalDate
import java.util.TimeZone

/** Everything the Stats screen draws, loaded separately so one slow query shows alone. */
data class StatsData(
    val lifetime: Loadable<LifetimeStatsDto> = Loadable.Loading,
    val achievements: Loadable<AchievementsDto> = Loadable.Loading,
    val week: Loadable<List<DayStatDto>> = Loadable.Loading,
    val heatmap: Loadable<List<DayStatDto>> = Loadable.Loading,
)

/** The device's current offset from UTC, which the bridge uses to find "today". */
fun tzOffsetMinutes(): Int = TimeZone.getDefault().getOffset(System.currentTimeMillis()) / 60_000

@Composable
fun StatsScreen(container: AppContainer, onBack: () -> Unit) {
    val scope = rememberCoroutineScope()
    val api = container.http.discovery
    val lifetime = rememberLoadable(Unit, "stats.lifetime") { api.lifetimeStats() }
    val achievements = rememberLoadable(Unit, "stats.achievements") { api.achievements(tzOffsetMinutes()) }
    val week = rememberLoadable(Unit, "stats.week") { api.weeklyStats() }
    val heatmap = rememberLoadable(Unit, "stats.heatmap") { api.heatmap() }

    StatsContent(
        data = StatsData(lifetime.state, achievements.state, week.state, heatmap.state),
        today = LocalDate.now(),
        onBack = onBack,
        onRetry = { listOf(lifetime, achievements, week, heatmap).forEach { it.reload() } },
        onPlay = { title, artist -> scope.launch { container.searchAndPlay(title, artist) } },
    )
}

@Composable
fun StatsContent(
    data: StatsData,
    today: LocalDate,
    onBack: () -> Unit,
    onRetry: () -> Unit,
    onPlay: (String, String?) -> Unit,
) {
    Column(Modifier.fillMaxSize().verticalScroll(rememberScrollState()).padding(bottom = 28.dp)) {
        DetailHeader("Stats", "Every play, on every device signed in to this account", onBack)

        LoadableSection(data.lifetime, onRetry) { stats ->
            val streak = data.achievements.valueOrNull
            Column(Modifier.padding(horizontal = 16.dp), verticalArrangement = Arrangement.spacedBy(10.dp)) {
                Row(horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                    StatTile("%,d".format(stats.totalPlays), "Total plays", Icons.Filled.PlayCircle, SectionTint.Library, Modifier.weight(1f))
                    StatTile(stats.totalListenSeconds.asListeningTime(), "Listening time", Icons.Filled.Schedule, SectionTint.Recent, Modifier.weight(1f))
                }
                Row(horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                    StatTile(
                        streak?.let { "${it.currentStreakDays} day${if (it.currentStreakDays == 1) "" else "s"}" } ?: "—",
                        "Current streak",
                        Icons.Filled.LocalFireDepartment,
                        SectionTint.Device,
                        Modifier.weight(1f),
                    )
                    StatTile(
                        streak?.let { "${it.longestStreakDays} day${if (it.longestStreakDays == 1) "" else "s"}" } ?: "—",
                        "Longest streak",
                        Icons.Filled.WorkspacePremium,
                        SectionTint.Favorites,
                        Modifier.weight(1f),
                    )
                }
            }

            data.week.valueOrNull?.let { week ->
                IconSectionHeader(Icons.Filled.BarChart, "This week", tint = SectionTint.Library)
                WeekBars(week, today)
                Text(
                    "${week.sumOf { it.plays }} plays · ${week.sumOf { it.listenSeconds }.asListeningTime()} in the last 7 days",
                    style = MaterialTheme.typography.labelSmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    modifier = Modifier.padding(horizontal = 20.dp, vertical = 6.dp),
                )
            }

            data.heatmap.valueOrNull?.let { days ->
                IconSectionHeader(Icons.Filled.GridOn, "Your year", tint = SectionTint.Offline)
                ListeningHeatmap(days, today)
                Text(
                    "${days.size} days with music in the last year",
                    style = MaterialTheme.typography.labelSmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    modifier = Modifier.padding(horizontal = 20.dp, vertical = 6.dp),
                )
            }

            if (stats.topArtists.isNotEmpty()) {
                IconSectionHeader(Icons.Filled.Person, "Top artists", tint = SectionTint.Favorites)
                stats.topArtists.forEachIndexed { index, artist ->
                    RankedRow(index + 1, artist.artist, null, "${artist.playCount} plays")
                }
            }
            if (stats.topTracks.isNotEmpty()) {
                IconSectionHeader(Icons.Filled.MusicNote, "Top tracks", tint = SectionTint.Playlists)
                stats.topTracks.forEachIndexed { index, track ->
                    RankedRow(
                        index + 1,
                        track.title,
                        trackSubtitle(track.title, track.artist, null),
                        "${track.playCount} plays",
                        onClick = { onPlay(track.title, track.artist) },
                    )
                }
            }
            Spacer(Modifier.height(8.dp))
            Text(
                "Plays count once a track has played for five seconds, from Lumisound or LumiMusic alike.",
                style = MaterialTheme.typography.labelSmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                modifier = Modifier.fillMaxWidth().padding(horizontal = 20.dp, vertical = 8.dp),
            )
        }
    }
}

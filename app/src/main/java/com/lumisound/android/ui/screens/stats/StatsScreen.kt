package com.lumisound.android.ui.screens.stats

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.IntrinsicSize
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.ui.Alignment
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.unit.sp
import com.lumisound.android.ui.components.GlassPanel
import com.lumisound.android.ui.theme.LocalLumiPalette
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

/**
 * Stats as a report rather than a table: one enormous number first (how long you have
 * listened, which is the number people actually want), then a bento of the smaller facts,
 * the week, the whole year as a heatmap, and top artists as bars sized by their plays.
 */
@Composable
fun StatsContent(
    data: StatsData,
    today: LocalDate,
    onBack: () -> Unit,
    onRetry: () -> Unit,
    onPlay: (String, String?) -> Unit,
) {
    val palette = LocalLumiPalette.current
    Column(Modifier.fillMaxSize().verticalScroll(rememberScrollState()).padding(bottom = 28.dp)) {
        DetailHeader("Your listening", "Every play, on every device on this account", onBack, eyebrow = "Report")

        LoadableSection(data.lifetime, onRetry) { stats ->
            val streak = data.achievements.valueOrNull
            // The hero number.
            Column(Modifier.padding(horizontal = 20.dp, vertical = 6.dp)) {
                Row(verticalAlignment = Alignment.Bottom) {
                    Text(
                        "${stats.totalListenSeconds / 3600}",
                        style = MaterialTheme.typography.displayLarge.copy(fontSize = 88.sp, lineHeight = 88.sp),
                        color = palette.accent,
                    )
                    Spacer(Modifier.width(8.dp))
                    Text("hours", style = MaterialTheme.typography.headlineSmall, modifier = Modifier.padding(bottom = 14.dp))
                }
                Text(
                    "of music across ${"%,d".format(stats.totalPlays)} plays",
                    style = MaterialTheme.typography.bodyLarge,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }
            Spacer(Modifier.height(16.dp))

            // The bento: one tall tile beside two short ones.
            Row(Modifier.padding(horizontal = 16.dp).height(IntrinsicSize.Min), horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                StatTile(
                    streak?.let { "${it.currentStreakDays}" } ?: "—",
                    "day streak right now",
                    Icons.Filled.LocalFireDepartment,
                    SectionTint.Device,
                    Modifier.weight(1f).fillMaxHeight(),
                )
                Column(Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(10.dp)) {
                    StatTile(streak?.let { "${it.longestStreakDays}d" } ?: "—", "longest streak", Icons.Filled.WorkspacePremium, SectionTint.Favorites, Modifier.fillMaxWidth())
                    StatTile("${stats.topArtists.size}", "artists in your top", Icons.Filled.Person, SectionTint.Library, Modifier.fillMaxWidth())
                }
            }

            data.week.valueOrNull?.let { week ->
                IconSectionHeader(Icons.Filled.BarChart, "This week", tint = SectionTint.Library)
                GlassPanel {
                    WeekBars(week, today, Modifier.padding(horizontal = 0.dp))
                    Text(
                        "${week.sumOf { it.plays }} plays · ${week.sumOf { it.listenSeconds }.asListeningTime()}",
                        style = MaterialTheme.typography.labelMedium,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                        modifier = Modifier.padding(top = 6.dp),
                    )
                }
            }

            data.heatmap.valueOrNull?.let { days ->
                IconSectionHeader(Icons.Filled.GridOn, "Your year", tint = SectionTint.Offline)
                GlassPanel {
                    ListeningHeatmap(days, today)
                    Text(
                        "${days.size} days with music in the last year",
                        style = MaterialTheme.typography.labelMedium,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                        modifier = Modifier.padding(top = 8.dp),
                    )
                }
            }

            if (stats.topArtists.isNotEmpty()) {
                IconSectionHeader(Icons.Filled.Person, "Top artists", tint = SectionTint.Favorites)
                val max = stats.topArtists.maxOf { it.playCount }.coerceAtLeast(1)
                Column(Modifier.padding(horizontal = 16.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                    stats.topArtists.forEachIndexed { index, artist ->
                        ArtistBar(index + 1, artist.artist, artist.playCount, artist.playCount.toFloat() / max)
                    }
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
            Text(
                "A play counts once a track has played for five seconds, in Lumisound or LumiMusic.",
                style = MaterialTheme.typography.labelSmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                modifier = Modifier.fillMaxWidth().padding(horizontal = 20.dp, vertical = 14.dp),
            )
        }
    }
}

/** An artist as a bar whose length is their share of the top artist's plays. */
@Composable
private fun ArtistBar(rank: Int, name: String, plays: Int, fraction: Float) {
    val palette = LocalLumiPalette.current
    Box(Modifier.fillMaxWidth().height(52.dp).clip(RoundedCornerShape(16.dp)).background(palette.elevatedSurface)) {
        Box(
            Modifier
                .fillMaxWidth(fraction.coerceIn(0.08f, 1f))
                .fillMaxHeight()
                .background(Brush.horizontalGradient(listOf(palette.accent.copy(alpha = 0.55f), palette.accent.copy(alpha = 0.15f))))
        )
        Row(Modifier.fillMaxSize().padding(horizontal = 14.dp), verticalAlignment = Alignment.CenterVertically) {
            Text("$rank", style = MaterialTheme.typography.titleMedium, modifier = Modifier.width(26.dp))
            Text(name, style = MaterialTheme.typography.titleSmall, modifier = Modifier.weight(1f), maxLines = 1)
            Text("$plays", style = MaterialTheme.typography.labelLarge)
        }
    }
}

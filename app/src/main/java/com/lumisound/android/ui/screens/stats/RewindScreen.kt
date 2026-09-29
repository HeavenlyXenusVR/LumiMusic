package com.lumisound.android.ui.screens.stats

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.AllInclusive
import androidx.compose.material.icons.filled.CalendarMonth
import androidx.compose.material.icons.filled.CalendarToday
import androidx.compose.material.icons.filled.Share
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import com.lumisound.android.AppContainer
import com.lumisound.android.bridge.model.LifetimeStatsDto
import com.lumisound.android.bridge.model.ReviewDto
import com.lumisound.android.ui.components.ChipRow
import com.lumisound.android.ui.components.Loadable
import com.lumisound.android.ui.components.LoadableSection
import com.lumisound.android.ui.components.NavChip
import com.lumisound.android.ui.components.OneLine
import com.lumisound.android.ui.components.rememberLoadable
import com.lumisound.android.ui.theme.LocalLumiPalette
import com.lumisound.android.ui.theme.SectionTint
import java.time.LocalDate
import java.time.Month
import java.time.format.TextStyle
import java.util.Locale

enum class RewindPeriod(val label: String) { AllTime("All time"), Month("This month"), Year("This year") }

/**
 * A recap in one card, after Lumisound's Rewind: All Time from the lifetime totals, This
 * Month and This Year from the review endpoints, which add distinct counts, average BPM
 * and the peak day. Shareable as text, since the numbers are the point.
 */
data class RewindSummary(
    val heading: String,
    val plays: Int,
    val listenSeconds: Long,
    val topArtists: List<String>,
    val topTracks: List<Pair<String, String?>>,
    val distinctArtists: Int? = null,
    val distinctTracks: Int? = null,
    val averageBpm: Double? = null,
    val peakDay: String? = null,
)

fun LifetimeStatsDto.toSummary() = RewindSummary(
    heading = "All time",
    plays = totalPlays,
    listenSeconds = totalListenSeconds,
    topArtists = topArtists.map { it.artist },
    topTracks = topTracks.map { it.title to it.artist },
)

fun ReviewDto.toSummary(): RewindSummary {
    val heading = month?.let { "${Month.of(it).getDisplayName(TextStyle.FULL, Locale.getDefault())} $year" } ?: "$year"
    return RewindSummary(
        heading = heading,
        plays = totalPlays,
        listenSeconds = totalListenSeconds,
        topArtists = topArtists.map { it.artist },
        topTracks = topTracks.map { it.title to it.artist },
        distinctArtists = distinctArtists,
        distinctTracks = distinctTracks,
        averageBpm = averageBpm,
        peakDay = peakDay?.date?.let { runCatching { LocalDate.parse(it) }.getOrNull() }?.let {
            "${it.dayOfWeek.getDisplayName(TextStyle.FULL, Locale.getDefault())}, ${it.month.getDisplayName(TextStyle.SHORT, Locale.getDefault())} ${it.dayOfMonth}"
        },
    )
}

/** The plain-text version for the share sheet. */
fun RewindSummary.asShareText(): String = buildString {
    appendLine("My LumiMusic Rewind — $heading")
    appendLine("$plays plays · ${listenSeconds.asListeningTime()} listened")
    if (topArtists.isNotEmpty()) appendLine("Top artists: ${topArtists.take(3).joinToString(", ")}")
    topTracks.firstOrNull()?.let { (title, artist) ->
        appendLine("Top track: $title${artist?.takeIf { it.isNotBlank() }?.let { " — $it" }.orEmpty()}")
    }
    peakDay?.let { appendLine("Peak day: $it") }
}.trim()

@Composable
fun RewindScreen(container: AppContainer, onBack: () -> Unit) {
    val context = LocalContext.current
    var period by rememberSaveable { mutableStateOf(RewindPeriod.Month) }
    val api = container.http.discovery
    val summary = rememberLoadable(period, "rewind.$period") {
        when (period) {
            RewindPeriod.AllTime -> api.lifetimeStats().toSummary()
            RewindPeriod.Month -> api.monthInReview().toSummary()
            RewindPeriod.Year -> api.yearInReview().toSummary()
        }
    }
    RewindContent(
        period = period,
        summary = summary.state,
        onPeriodChange = { period = it },
        onBack = onBack,
        onRetry = summary::reload,
        onShare = { text ->
            val send = android.content.Intent(android.content.Intent.ACTION_SEND).apply {
                type = "text/plain"
                putExtra(android.content.Intent.EXTRA_TEXT, text)
            }
            context.startActivity(android.content.Intent.createChooser(send, "Share your Rewind"))
        },
    )
}

@Composable
fun RewindContent(
    period: RewindPeriod,
    summary: Loadable<RewindSummary>,
    onPeriodChange: (RewindPeriod) -> Unit,
    onBack: () -> Unit,
    onRetry: () -> Unit,
    onShare: (String) -> Unit,
) {
    val palette = LocalLumiPalette.current
    Column(Modifier.fillMaxSize().verticalScroll(rememberScrollState()).padding(bottom = 28.dp)) {
        DetailHeader("Rewind", "Your listening, wrapped", onBack, trailing = {
            summary.valueOrNull?.let { s ->
                IconButton(onClick = { onShare(s.asShareText()) }) {
                    Icon(Icons.Filled.Share, contentDescription = "Share")
                }
            }
        })
        ChipRow(
            RewindPeriod.entries.map { entry ->
                NavChip(
                    label = entry.label,
                    icon = when (entry) {
                        RewindPeriod.AllTime -> Icons.Filled.AllInclusive
                        RewindPeriod.Month -> Icons.Filled.CalendarToday
                        RewindPeriod.Year -> Icons.Filled.CalendarMonth
                    },
                    selected = entry == period,
                    onClick = { onPeriodChange(entry) },
                )
            }
        )
        LoadableSection(summary, onRetry) { s ->
            Box(
                Modifier
                    .fillMaxWidth()
                    .padding(horizontal = 16.dp)
                    .clip(RoundedCornerShape(26.dp))
                    .background(
                        Brush.linearGradient(
                            listOf(palette.accent, palette.accent.copy(alpha = 0.55f), SectionTint.Offline.copy(alpha = 0.7f))
                        )
                    )
                    .padding(22.dp)
            ) {
                Column {
                    Text(s.heading.uppercase(), style = MaterialTheme.typography.labelLarge, color = Color.White.copy(alpha = 0.85f))
                    Spacer(Modifier.height(10.dp))
                    Text(
                        s.listenSeconds.asListeningTime(),
                        style = MaterialTheme.typography.displaySmall.copy(fontWeight = FontWeight.Black),
                        color = Color.White,
                    )
                    Text("of music · ${"%,d".format(s.plays)} plays", style = MaterialTheme.typography.bodyMedium, color = Color.White)

                    if (s.topArtists.isNotEmpty()) {
                        Spacer(Modifier.height(18.dp))
                        Text("TOP ARTISTS", style = MaterialTheme.typography.labelSmall, color = Color.White.copy(alpha = 0.75f))
                        s.topArtists.take(5).forEachIndexed { index, artist ->
                            OneLine("${index + 1}. $artist", style = MaterialTheme.typography.titleSmall, color = Color.White)
                        }
                    }
                    if (s.topTracks.isNotEmpty()) {
                        Spacer(Modifier.height(14.dp))
                        Text("TOP TRACKS", style = MaterialTheme.typography.labelSmall, color = Color.White.copy(alpha = 0.75f))
                        s.topTracks.take(5).forEachIndexed { index, (title, artist) ->
                            OneLine(
                                "${index + 1}. $title" + (artist?.takeIf { it.isNotBlank() && it != title }?.let { " — $it" } ?: ""),
                                style = MaterialTheme.typography.bodyMedium,
                                color = Color.White,
                            )
                        }
                    }

                    val extras = listOfNotNull(
                        s.distinctArtists?.let { "$it" to "artists" },
                        s.distinctTracks?.let { "$it" to "tracks" },
                        s.averageBpm?.let { "${it.toInt()}" to "avg BPM" },
                    )
                    if (extras.isNotEmpty()) {
                        Spacer(Modifier.height(16.dp))
                        Row(horizontalArrangement = Arrangement.spacedBy(18.dp)) {
                            extras.forEach { (value, label) ->
                                Column {
                                    Text(value, style = MaterialTheme.typography.titleLarge, color = Color.White)
                                    Text(label, style = MaterialTheme.typography.labelSmall, color = Color.White.copy(alpha = 0.8f))
                                }
                            }
                        }
                    }
                    s.peakDay?.let {
                        Spacer(Modifier.height(14.dp))
                        Row(verticalAlignment = Alignment.CenterVertically) {
                            Text("Peak day", style = MaterialTheme.typography.labelSmall, color = Color.White.copy(alpha = 0.8f))
                            Spacer(Modifier.width(8.dp))
                            Text(it, style = MaterialTheme.typography.labelLarge, color = Color.White)
                        }
                    }
                }
            }
            if (s.plays == 0) {
                Text(
                    "Nothing played in this period yet.",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    modifier = Modifier.padding(20.dp),
                )
            }
        }
    }
}

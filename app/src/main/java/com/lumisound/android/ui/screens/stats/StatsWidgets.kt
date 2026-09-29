package com.lumisound.android.ui.screens.stats

import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.geometry.CornerRadius
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.unit.dp
import com.lumisound.android.bridge.model.DayStatDto
import com.lumisound.android.ui.components.LumiCard
import com.lumisound.android.ui.components.OneLine
import com.lumisound.android.ui.theme.LocalLumiPalette
import java.time.DayOfWeek
import java.time.LocalDate
import java.time.format.TextStyle
import java.time.temporal.ChronoUnit
import java.util.Locale

/** A pushed screen's header: back arrow, large title, optional line under it. */
@Composable
fun DetailHeader(title: String, subtitle: String? = null, onBack: () -> Unit, trailing: (@Composable () -> Unit)? = null) {
    Row(
        Modifier.fillMaxWidth().padding(start = 6.dp, end = 12.dp, top = 8.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        IconButton(onClick = onBack) {
            Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = "Back")
        }
        Spacer(Modifier.weight(1f))
        trailing?.invoke()
    }
    Column(Modifier.padding(start = 20.dp, end = 20.dp, bottom = 10.dp)) {
        Text(title, style = MaterialTheme.typography.displaySmall)
        subtitle?.let {
            Text(it, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
        }
    }
}

/** One big number with a label, in a card -- the unit every stats screen is built from. */
@Composable
fun StatTile(value: String, label: String, icon: ImageVector, tint: Color, modifier: Modifier = Modifier) {
    LumiCard(modifier) {
        Column(Modifier.padding(14.dp)) {
            Box(
                Modifier.size(28.dp).clip(RoundedCornerShape(8.dp)).background(tint.copy(alpha = 0.18f)),
                contentAlignment = Alignment.Center,
            ) {
                Icon(icon, contentDescription = null, tint = tint, modifier = Modifier.size(16.dp))
            }
            Spacer(Modifier.height(10.dp))
            OneLine(value, style = MaterialTheme.typography.titleLarge)
            OneLine(label, style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
        }
    }
}

/** "12h 04m", "38m", "0m" -- listening time the way a person reads it. */
fun Long.asListeningTime(): String {
    val hours = this / 3600
    val minutes = (this % 3600) / 60
    return when {
        hours >= 100 -> "${hours}h"
        hours > 0 -> "${hours}h ${"%02d".format(minutes)}m"
        else -> "${minutes}m"
    }
}

/** A ranked row: position, title, secondary line, and a count on the right. */
@Composable
fun RankedRow(rank: Int, title: String, subtitle: String?, trailing: String, onClick: (() -> Unit)? = null) {
    Row(
        Modifier
            .fillMaxWidth()
            .then(if (onClick != null) Modifier.clickable(onClick = onClick) else Modifier)
            .padding(horizontal = 20.dp, vertical = 9.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(14.dp),
    ) {
        Text("$rank", style = MaterialTheme.typography.titleMedium, color = LocalLumiPalette.current.accent, modifier = Modifier.width(22.dp))
        Column(Modifier.weight(1f)) {
            OneLine(title, style = MaterialTheme.typography.bodyMedium)
            subtitle?.let {
                OneLine(it, style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
            }
        }
        Text(trailing, style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
    }
}

/**
 * Seven bars for the last seven days, today last. The bridge omits days with no plays, so
 * the week is rebuilt from the calendar and missing days drawn as empty rather than
 * collapsing the chart to however many days happened to have music.
 */
@Composable
fun WeekBars(days: List<DayStatDto>, today: LocalDate, modifier: Modifier = Modifier) {
    val palette = LocalLumiPalette.current
    val byDate = days.associateBy { it.date }
    val week = (6 downTo 0).map { today.minusDays(it.toLong()) }
    val values = week.map { (byDate[it.toString()]?.listenSeconds ?: 0L).toFloat() }
    val max = values.maxOrNull()?.takeIf { it > 0f } ?: 1f
    Row(
        modifier.fillMaxWidth().height(130.dp).padding(horizontal = 20.dp),
        horizontalArrangement = Arrangement.spacedBy(10.dp),
        verticalAlignment = Alignment.Bottom,
    ) {
        week.forEachIndexed { index, date ->
            Column(Modifier.weight(1f), horizontalAlignment = Alignment.CenterHorizontally) {
                val fraction = values[index] / max
                Box(
                    Modifier
                        .fillMaxWidth()
                        .height((96 * fraction).dp.coerceAtLeast(4.dp))
                        .clip(RoundedCornerShape(6.dp))
                        .background(if (values[index] > 0f) palette.accent else palette.hairline)
                )
                Spacer(Modifier.height(6.dp))
                Text(
                    date.dayOfWeek.getDisplayName(TextStyle.NARROW, Locale.getDefault()),
                    style = MaterialTheme.typography.labelSmall,
                    color = if (date == today) palette.accent else MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }
        }
    }
}

/**
 * The GitHub-style year of listening: one column per week, Monday at the top, darker for
 * more plays. Intensity is bucketed by quartile of the account's own active days rather
 * than a fixed scale, so a light listener's heaviest day still reads as heavy.
 */
@Composable
fun ListeningHeatmap(days: List<DayStatDto>, today: LocalDate, modifier: Modifier = Modifier) {
    val palette = LocalLumiPalette.current
    val plays = days.associate { it.date to it.plays }
    val levels = heatmapLevels(days.map { it.plays })
    val start = today.minusDays(364).with(DayOfWeek.MONDAY)
    val weeks = (ChronoUnit.DAYS.between(start, today) / 7 + 1).toInt()
    val cell = 11.dp
    val gap = 3.dp
    Box(modifier.fillMaxWidth().horizontalScroll(rememberScrollState(initial = Int.MAX_VALUE)).padding(horizontal = 20.dp)) {
        Canvas(Modifier.width((cell + gap) * weeks).height((cell + gap) * 7)) {
            val cellPx = cell.toPx()
            val step = cellPx + gap.toPx()
            for (week in 0 until weeks) {
                for (day in 0 until 7) {
                    val date = start.plusDays((week * 7 + day).toLong())
                    if (date.isAfter(today)) continue
                    val count = plays[date.toString()] ?: 0
                    val level = levels(count)
                    val color = if (level == 0) palette.hairline else palette.accent.copy(alpha = 0.25f + 0.25f * (level - 1))
                    drawRoundRect(
                        color = color,
                        topLeft = Offset(week * step, day * step),
                        size = Size(cellPx, cellPx),
                        cornerRadius = CornerRadius(cellPx * 0.25f),
                    )
                }
            }
        }
    }
}

/**
 * Maps a day's play count to 0 (none) through 4 (heaviest quartile) using the quartiles of
 * the days that had any plays at all.
 */
fun heatmapLevels(counts: List<Int>): (Int) -> Int {
    val active = counts.filter { it > 0 }.sorted()
    if (active.isEmpty()) return { 0 }
    fun quantile(q: Double) = active[((active.size - 1) * q).toInt()]
    val q1 = quantile(0.25)
    val q2 = quantile(0.5)
    val q3 = quantile(0.75)
    return { count ->
        when {
            count <= 0 -> 0
            count <= q1 -> 1
            count <= q2 -> 2
            count <= q3 -> 3
            else -> 4
        }
    }
}

/** A thin labelled progress bar, for badge progress and goal-style rows. */
@Composable
fun ProgressLine(fraction: Float, modifier: Modifier = Modifier) {
    val palette = LocalLumiPalette.current
    Box(modifier.fillMaxWidth().height(5.dp).clip(CircleShape).background(palette.hairline)) {
        Box(Modifier.fillMaxWidth(fraction.coerceIn(0f, 1f)).height(5.dp).clip(CircleShape).background(palette.accent))
    }
}

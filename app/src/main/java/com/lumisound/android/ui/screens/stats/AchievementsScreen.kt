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
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Bedtime
import androidx.compose.material.icons.filled.CalendarMonth
import androidx.compose.material.icons.filled.EventRepeat
import androidx.compose.material.icons.filled.HourglassBottom
import androidx.compose.material.icons.filled.Inventory2
import androidx.compose.material.icons.filled.LocalFireDepartment
import androidx.compose.material.icons.filled.Lock
import androidx.compose.material.icons.filled.PlayCircle
import androidx.compose.material.icons.filled.Public
import androidx.compose.material.icons.filled.Repeat
import androidx.compose.material.icons.filled.Schedule
import androidx.compose.material.icons.filled.Shuffle
import androidx.compose.material.icons.filled.DirectionsRun
import androidx.compose.material.icons.filled.Star
import androidx.compose.material.icons.filled.Timer
import androidx.compose.material.icons.filled.Verified
import androidx.compose.material.icons.filled.WbTwilight
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.GenericShape
import androidx.compose.ui.graphics.Brush
import com.lumisound.android.ui.aura.Aura
import com.lumisound.android.ui.components.Eyebrow
import com.lumisound.android.ui.components.GlassPanel
import com.lumisound.android.ui.components.RingProgress
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import com.lumisound.android.AppContainer
import com.lumisound.android.bridge.model.AchievementsDto
import com.lumisound.android.ui.components.Loadable
import com.lumisound.android.ui.components.LoadableSection
import com.lumisound.android.ui.components.LumiCard
import com.lumisound.android.ui.components.OneLine
import com.lumisound.android.ui.components.rememberLoadable
import com.lumisound.android.ui.theme.LocalLumiPalette
import com.lumisound.android.ui.theme.SectionTint

/**
 * One badge the bridge can award. Ids, titles and requirements are Lumisound's own
 * (`AchievementsView.allBadges`), so a badge reads the same on both apps; [progress] is
 * filled in where the server's totals are enough to say how close it is.
 */
data class BadgeSpec(
    val id: String,
    val title: String,
    val icon: ImageVector,
    val requirement: String,
    val progress: ((AchievementsDto) -> Float)? = null,
)

private fun playsTo(n: Int): (AchievementsDto) -> Float = { it.totalPlays / n.toFloat() }
private fun hoursTo(n: Int): (AchievementsDto) -> Float = { it.totalListenSeconds / 3600f / n }
private fun streakTo(n: Int): (AchievementsDto) -> Float = { it.longestStreakDays / n.toFloat() }

val ALL_BADGES = listOf(
    BadgeSpec("plays_10", "10 Plays", Icons.Filled.PlayCircle, "Play 10 tracks.", playsTo(10)),
    BadgeSpec("plays_50", "50 Plays", Icons.Filled.PlayCircle, "Reach 50 total plays.", playsTo(50)),
    BadgeSpec("plays_100", "100 Plays", Icons.Filled.Repeat, "Reach 100 total plays.", playsTo(100)),
    BadgeSpec("plays_500", "500 Plays", Icons.Filled.Repeat, "Reach 500 total plays.", playsTo(500)),
    BadgeSpec("plays_1000", "1000 Plays", Icons.Filled.Star, "Reach 1,000 total plays.", playsTo(1000)),
    BadgeSpec("hours_1", "1 Hour Listened", Icons.Filled.Schedule, "Spend 1 hour listening.", hoursTo(1)),
    BadgeSpec("hours_10", "10 Hours Listened", Icons.Filled.Schedule, "Listen for 10 hours in total.", hoursTo(10)),
    BadgeSpec("hours_24", "24 Hours Listened", Icons.Filled.Timer, "A full day of listening time.", hoursTo(24)),
    BadgeSpec("hours_100", "100 Hours Listened", Icons.Filled.HourglassBottom, "Listen for 100 hours in total.", hoursTo(100)),
    BadgeSpec("streak_3", "3-Day Streak", Icons.Filled.LocalFireDepartment, "Listen on 3 days in a row.", streakTo(3)),
    BadgeSpec("streak_7", "Week Streak", Icons.Filled.LocalFireDepartment, "Keep a streak for 7 days.", streakTo(7)),
    BadgeSpec("streak_30", "Month Streak", Icons.Filled.CalendarMonth, "Listen every day for 30 days.", streakTo(30)),
    BadgeSpec("streak_100", "100-Day Streak", Icons.Filled.EventRepeat, "A 100-day daily streak.", streakTo(100)),
    BadgeSpec("night_owl", "Night Owl", Icons.Filled.Bedtime, "Listen between midnight and 5 AM."),
    BadgeSpec("early_bird", "Early Bird", Icons.Filled.WbTwilight, "Listen between 5 AM and 8 AM."),
    BadgeSpec("marathon", "Marathon", Icons.Filled.DirectionsRun, "Listen for 3+ hours in a single day."),
    BadgeSpec("crate_digger", "Crate Digger", Icons.Filled.Inventory2, "Build a cloud library of 100+ tracks."),
    BadgeSpec("globe_trotter", "Globe Trotter", Icons.Filled.Public, "Listen to 25 different artists."),
    BadgeSpec("completionist", "Completionist", Icons.Filled.Verified, "Play 15 different tracks by one artist."),
    BadgeSpec("shuffle_master", "Shuffle Master", Icons.Filled.Shuffle, "Play 200 different tracks."),
)

@Composable
fun AchievementsScreen(container: AppContainer, onBack: () -> Unit) {
    val achievements = rememberLoadable(Unit, "achievements") { container.http.discovery.achievements(tzOffsetMinutes()) }
    AchievementsContent(achievements.state, onBack, achievements::reload)
}

/** A hexagon, for badge medallions. */
private val Hexagon = GenericShape { size, _ ->
    val w = size.width
    val h = size.height
    moveTo(w * 0.5f, 0f)
    lineTo(w, h * 0.25f)
    lineTo(w, h * 0.75f)
    lineTo(w * 0.5f, h)
    lineTo(0f, h * 0.75f)
    lineTo(0f, h * 0.25f)
    close()
}

/**
 * Achievements as a trophy case: a level ring for how many are earned, the single closest
 * badge still to win (with how close), then every badge as a hexagonal medallion -- struck
 * in colour when earned, a dark blank with a lock and a progress bar when not.
 */
@Composable
fun AchievementsContent(achievements: Loadable<AchievementsDto>, onBack: () -> Unit, onRetry: () -> Unit) {
    val palette = LocalLumiPalette.current
    Column(Modifier.fillMaxSize().verticalScroll(rememberScrollState()).padding(bottom = 28.dp)) {
        DetailHeader("Trophy case", null, onBack, eyebrow = "Achievements")
        LoadableSection(achievements, onRetry) { data ->
            val earned = data.badges.toSet()
            val fraction = earned.size / ALL_BADGES.size.toFloat()

            Row(Modifier.fillMaxWidth().padding(horizontal = 20.dp), verticalAlignment = Alignment.CenterVertically) {
                Box(Modifier.size(132.dp), contentAlignment = Alignment.Center) {
                    RingProgress(fraction, Modifier.size(132.dp), stroke = 10.dp)
                    Column(horizontalAlignment = Alignment.CenterHorizontally) {
                        Eyebrow("Level")
                        Text("${earned.size}", style = MaterialTheme.typography.displayMedium)
                    }
                }
                Spacer(Modifier.width(18.dp))
                Column(Modifier.weight(1f)) {
                    Text("${earned.size} of ${ALL_BADGES.size} badges", style = MaterialTheme.typography.titleLarge)
                    Spacer(Modifier.height(4.dp))
                    Text(
                        "${data.currentStreakDays}-day streak · best ${data.longestStreakDays}",
                        style = MaterialTheme.typography.bodyMedium,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                }
            }

            // The nearest badge not yet earned that has a measurable distance.
            ALL_BADGES
                .filter { it.id !in earned && it.progress != null }
                .maxByOrNull { it.progress!!(data).coerceAtMost(0.999f) }
                ?.let { next ->
                    Spacer(Modifier.height(18.dp))
                    GlassPanel {
                        Row(verticalAlignment = Alignment.CenterVertically) {
                            Medallion(next, unlocked = false, size = 58.dp)
                            Spacer(Modifier.width(14.dp))
                            Column(Modifier.weight(1f)) {
                                Eyebrow("Next up", color = palette.accent)
                                Text(next.title, style = MaterialTheme.typography.titleMedium)
                                Text(next.requirement, style = MaterialTheme.typography.labelMedium, color = MaterialTheme.colorScheme.onSurfaceVariant)
                                Spacer(Modifier.height(8.dp))
                                ProgressLine(next.progress!!(data))
                            }
                        }
                    }
                }

            Spacer(Modifier.height(20.dp))
            Column(Modifier.padding(horizontal = 16.dp), verticalArrangement = Arrangement.spacedBy(18.dp)) {
                ALL_BADGES.sortedByDescending { it.id in earned }.chunked(3).forEach { row ->
                    Row(horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                        row.forEach { badge ->
                            val unlocked = badge.id in earned
                            Column(Modifier.weight(1f), horizontalAlignment = Alignment.CenterHorizontally) {
                                Medallion(badge, unlocked, 78.dp)
                                Spacer(Modifier.height(8.dp))
                                Text(
                                    badge.title,
                                    style = MaterialTheme.typography.labelLarge,
                                    textAlign = TextAlign.Center,
                                    color = if (unlocked) Color.White else MaterialTheme.colorScheme.onSurfaceVariant,
                                    maxLines = 2,
                                )
                                if (!unlocked) {
                                    badge.progress?.let { progress ->
                                        Spacer(Modifier.height(5.dp))
                                        ProgressLine(progress(data), Modifier.padding(horizontal = 14.dp))
                                    }
                                }
                            }
                        }
                        repeat(3 - row.size) { Spacer(Modifier.weight(1f)) }
                    }
                }
            }
        }
    }
}

@Composable
private fun Medallion(badge: BadgeSpec, unlocked: Boolean, size: androidx.compose.ui.unit.Dp) {
    val palette = LocalLumiPalette.current
    val tint = Aura.forKey(badge.id).primary
    Box(
        Modifier
            .size(size)
            .clip(Hexagon)
            .background(
                if (unlocked) Brush.linearGradient(listOf(tint, palette.accent))
                else Brush.linearGradient(listOf(Color.White.copy(alpha = 0.08f), Color.White.copy(alpha = 0.03f)))
            ),
        contentAlignment = Alignment.Center,
    ) {
        Icon(
            if (unlocked) badge.icon else Icons.Filled.Lock,
            contentDescription = null,
            tint = if (unlocked) Color.White else Color.White.copy(alpha = 0.35f),
            modifier = Modifier.size(size * 0.4f),
        )
    }
}

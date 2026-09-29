package com.lumisound.android.ui.screens.home

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.AutoAwesome
import androidx.compose.material.icons.filled.BarChart
import androidx.compose.material.icons.filled.CalendarMonth
import androidx.compose.material.icons.filled.EmojiEvents
import androidx.compose.material.icons.filled.Explore
import androidx.compose.material.icons.filled.History
import androidx.compose.material.icons.filled.Notifications
import androidx.compose.material.icons.filled.People
import androidx.compose.material.icons.filled.PlayArrow
import androidx.compose.material.icons.filled.Podcasts
import androidx.compose.material.icons.filled.Refresh
import androidx.compose.material.icons.filled.Replay
import androidx.compose.material.icons.filled.Shuffle
import androidx.compose.material.icons.filled.TrendingUp
import androidx.compose.material.icons.filled.ViewWeek
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import com.lumisound.android.bridge.model.CommunityTrackDto
import com.lumisound.android.bridge.model.DailyPickResponse
import com.lumisound.android.bridge.model.EpisodeProgressDto
import com.lumisound.android.bridge.model.HistoryEntryDto
import com.lumisound.android.bridge.model.OnThisDayGroupDto
import com.lumisound.android.bridge.model.StreamTrackDto
import com.lumisound.android.bridge.model.TwinResponse
import com.lumisound.android.bridge.model.WeeklyMixResponse
import com.lumisound.android.ui.components.Artwork
import com.lumisound.android.ui.components.CapsuleToolbar
import com.lumisound.android.ui.components.IconSectionHeader
import com.lumisound.android.ui.components.Loadable
import com.lumisound.android.ui.components.LumiCard
import com.lumisound.android.ui.components.OneLine
import com.lumisound.android.ui.components.Pill
import com.lumisound.android.ui.components.ShelfCard
import com.lumisound.android.ui.components.ToolbarAction
import com.lumisound.android.ui.components.streamSubtitle
import com.lumisound.android.ui.components.trackSubtitle
import com.lumisound.android.ui.theme.LocalLumiPalette
import com.lumisound.android.ui.theme.SectionTint
import java.time.LocalTime

/** Everything the dashboard shows, each section loading on its own. */
data class HomeData(
    val displayName: String?,
    val unreadNotifications: Int = 0,
    val dailyPick: Loadable<DailyPickResponse> = Loadable.Loading,
    val weeklyMix: Loadable<WeeklyMixResponse> = Loadable.Loading,
    val discoverMix: Loadable<List<StreamTrackDto>> = Loadable.Loading,
    val continueListening: Loadable<List<EpisodeProgressDto>> = Loadable.Loading,
    val onThisDay: Loadable<List<OnThisDayGroupDto>> = Loadable.Loading,
    val recentlyPlayed: Loadable<List<HistoryEntryDto>> = Loadable.Loading,
    val trending: Loadable<List<CommunityTrackDto>> = Loadable.Loading,
    val twin: Loadable<TwinResponse> = Loadable.Loading,
)

/** Where the dashboard's shortcuts and cards lead. */
enum class HomeShortcut { Stats, Rewind, Achievements, Podcasts, Friends, Notifications }

data class HomeCallbacks(
    val onShortcut: (HomeShortcut) -> Unit = {},
    val onRefresh: () -> Unit = {},
    val onPlayStreams: (List<StreamTrackDto>, Int) -> Unit = { _, _ -> },
    val onPlayWeeklyMix: (Int, Boolean) -> Unit = { _, _ -> },
    val onResumeEpisode: (EpisodeProgressDto) -> Unit = {},
    val onPlayHistory: (HistoryEntryDto) -> Unit = {},
    val onPlayByName: (String, String?) -> Unit = { _, _ -> },
    val onPlayTwinMix: () -> Unit = {},
    val weeklyMixArtwork: (String) -> Any? = { null },
)

/**
 * The Home dashboard, after Lumisound's: a greeting, quick shortcuts, then shelves built
 * from the account's own listening. A section that fails or comes back empty is left out
 * rather than shown as an error -- one unreachable AI call should not put a red box in the
 * middle of someone's home screen.
 */
@Composable
fun HomeContent(data: HomeData, callbacks: HomeCallbacks) {
    LazyColumn(Modifier.fillMaxSize(), contentPadding = PaddingValues(bottom = 28.dp)) {
        item {
            Box(Modifier.fillMaxWidth(), contentAlignment = Alignment.Center) {
                CapsuleToolbar(
                    listOf(
                        ToolbarAction(Icons.Filled.Refresh, "Refresh", onClick = callbacks.onRefresh),
                        ToolbarAction(
                            Icons.Filled.Notifications,
                            "Notifications",
                            selected = data.unreadNotifications > 0,
                            onClick = { callbacks.onShortcut(HomeShortcut.Notifications) },
                        ),
                        ToolbarAction(Icons.Filled.BarChart, "Stats", onClick = { callbacks.onShortcut(HomeShortcut.Stats) }),
                    )
                )
            }
        }
        item { Greeting(data.displayName, data.unreadNotifications) }
        item { Shortcuts(callbacks.onShortcut) }

        data.dailyPick.valueOrNull?.let { daily ->
            val pick = daily.pick
            if (pick != null) item { DailyPickCard(pick, daily.reason, onPlay = { callbacks.onPlayStreams(listOf(pick), 0) }) }
        }

        shelf(
            loadable = data.weeklyMix,
            isEmpty = { it.tracks.isEmpty() },
            header = {
                IconSectionHeader(Icons.Filled.ViewWeek, "Weekly Mix", tint = SectionTint.Library, onSeeAll = null)
            },
        ) { mix ->
            item {
                PlayShuffleRow(
                    subtitle = "${mix.tracks.size} tracks from your cloud library, refreshed weekly",
                    onPlay = { callbacks.onPlayWeeklyMix(0, false) },
                    onShuffle = { callbacks.onPlayWeeklyMix(0, true) },
                )
            }
            item {
                LazyRow(contentPadding = PaddingValues(horizontal = 14.dp)) {
                    itemsIndexed(mix.tracks, key = { i, t -> "${t.relativePath}#$i" }) { index, track ->
                        ShelfCard(
                            title = track.title,
                            subtitle = trackSubtitle(track.title, track.artist, track.album),
                            artworkModel = if (track.hasArtwork) callbacks.weeklyMixArtwork(track.relativePath) else null,
                            fallbackKey = track.relativePath,
                            onClick = { callbacks.onPlayWeeklyMix(index, false) },
                        )
                    }
                }
            }
        }

        shelf(
            loadable = data.continueListening,
            isEmpty = { it.isEmpty() },
            header = { IconSectionHeader(Icons.Filled.Podcasts, "Continue listening", tint = SectionTint.Offline) },
        ) { episodes ->
            items(episodes.take(4), key = { "${it.feedUrl}#${it.episodeGuid}" }) { episode ->
                EpisodeProgressRow(episode, onClick = { callbacks.onResumeEpisode(episode) })
            }
        }

        shelf(
            loadable = data.discoverMix,
            isEmpty = { it.isEmpty() },
            header = { IconSectionHeader(Icons.Filled.Explore, "Discover Mix", tint = SectionTint.Recent) },
        ) { tracks ->
            item { StreamShelf(tracks, callbacks.onPlayStreams) }
        }

        shelf(
            loadable = data.onThisDay,
            isEmpty = { groups -> groups.all { it.tracks.isEmpty() } },
            header = { IconSectionHeader(Icons.Filled.CalendarMonth, "On this day", tint = SectionTint.Device) },
        ) { groups ->
            groups.filter { it.tracks.isNotEmpty() }.forEach { group ->
                item(key = "otd-${group.year}") {
                    Text(
                        if (group.yearsAgo == 1) "1 year ago · ${group.year}" else "${group.yearsAgo} years ago · ${group.year}",
                        style = MaterialTheme.typography.labelMedium,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                        modifier = Modifier.padding(start = 20.dp, bottom = 2.dp),
                    )
                }
                item(key = "otd-shelf-${group.year}") { StreamShelf(group.tracks, callbacks.onPlayStreams) }
            }
        }

        shelf(
            loadable = data.recentlyPlayed,
            isEmpty = { it.isEmpty() },
            header = { IconSectionHeader(Icons.Filled.History, "Recently played", tint = SectionTint.Favorites) },
        ) { history ->
            item {
                LazyRow(contentPadding = PaddingValues(horizontal = 14.dp)) {
                    itemsIndexed(history, key = { i, e -> e.id ?: "h$i" }) { _, entry ->
                        val title = entry.title ?: "Unknown"
                        ShelfCard(
                            title = title,
                            subtitle = trackSubtitle(title, entry.artist, null),
                            artworkModel = null,
                            fallbackKey = entry.localSongId ?: entry.trackUrl ?: title,
                            onClick = { callbacks.onPlayHistory(entry) },
                        )
                    }
                }
            }
        }

        shelf(
            loadable = data.trending,
            isEmpty = { it.isEmpty() },
            header = { IconSectionHeader(Icons.Filled.TrendingUp, "Trending with listeners", tint = SectionTint.Playlists) },
        ) { tracks ->
            itemsIndexed(tracks.take(8), key = { i, t -> "trend-$i-${t.title}" }) { index, track ->
                TrendingRow(index + 1, track, onClick = { callbacks.onPlayByName(track.title, track.artist) })
            }
        }

        data.twin.valueOrNull?.twin?.let { twin ->
            item { IconSectionHeader(Icons.Filled.People, "Your listening twin", tint = SectionTint.Favorites) }
            item {
                LumiCard(Modifier.fillMaxWidth().padding(horizontal = 16.dp), onClick = callbacks.onPlayTwinMix) {
                    Row(
                        Modifier.padding(16.dp),
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(14.dp),
                    ) {
                        Artwork(model = null, fallbackKey = twin.username, size = 54.dp, corner = 27.dp)
                        Column(Modifier.weight(1f)) {
                            OneLine(twin.displayName?.takeIf { it.isNotBlank() } ?: "@${twin.username}", style = MaterialTheme.typography.titleSmall)
                            Text(
                                twin.sharedArtists.take(4).joinToString(", ").ifEmpty { "Similar taste" },
                                style = MaterialTheme.typography.labelSmall,
                                color = MaterialTheme.colorScheme.onSurfaceVariant,
                                maxLines = 2,
                                overflow = TextOverflow.Ellipsis,
                            )
                        }
                        Column(horizontalAlignment = Alignment.End) {
                            Pill("${twin.similarity}% match")
                            Spacer(Modifier.height(6.dp))
                            Text("Play Twin Mix", style = MaterialTheme.typography.labelSmall, color = LocalLumiPalette.current.accent)
                        }
                    }
                }
            }
        }
    }
}

/**
 * Adds a dashboard section: header plus a spinner while loading, header plus [content]
 * once there is something, and nothing at all when it failed or came back empty.
 */
private fun <T> androidx.compose.foundation.lazy.LazyListScope.shelf(
    loadable: Loadable<T>,
    isEmpty: (T) -> Boolean,
    header: @Composable () -> Unit,
    content: androidx.compose.foundation.lazy.LazyListScope.(T) -> Unit,
) {
    when (loadable) {
        Loadable.Loading -> {
            item { header() }
            item {
                Box(Modifier.fillMaxWidth().height(72.dp), contentAlignment = Alignment.Center) {
                    CircularProgressIndicator(
                        color = LocalLumiPalette.current.accent,
                        strokeWidth = 2.dp,
                        modifier = Modifier.size(22.dp),
                    )
                }
            }
        }
        is Loadable.Ready -> if (!isEmpty(loadable.value)) {
            item { header() }
            content(loadable.value)
        }
        is Loadable.Failed -> Unit
    }
}

@Composable
private fun Greeting(name: String?, unread: Int) {
    val hour = LocalTime.now().hour
    val part = when (hour) {
        in 5..11 -> "Good morning"
        in 12..16 -> "Good afternoon"
        in 17..21 -> "Good evening"
        else -> "Up late"
    }
    Column(Modifier.padding(start = 20.dp, end = 20.dp, top = 2.dp, bottom = 6.dp)) {
        Text(
            if (name.isNullOrBlank()) part else "$part, $name",
            style = MaterialTheme.typography.displaySmall,
            maxLines = 2,
            overflow = TextOverflow.Ellipsis,
        )
        Text(
            if (unread > 0) "$unread unread notification${if (unread == 1) "" else "s"}" else "Your music, across every device",
            style = MaterialTheme.typography.bodySmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
        )
    }
}

private data class ShortcutTile(val shortcut: HomeShortcut, val label: String, val icon: ImageVector, val tint: Color)

@Composable
private fun Shortcuts(onShortcut: (HomeShortcut) -> Unit) {
    val tiles = listOf(
        ShortcutTile(HomeShortcut.Stats, "Stats", Icons.Filled.BarChart, SectionTint.Library),
        ShortcutTile(HomeShortcut.Rewind, "Rewind", Icons.Filled.Replay, SectionTint.Favorites),
        ShortcutTile(HomeShortcut.Achievements, "Badges", Icons.Filled.EmojiEvents, SectionTint.Device),
        ShortcutTile(HomeShortcut.Podcasts, "Podcasts", Icons.Filled.Podcasts, SectionTint.Offline),
        ShortcutTile(HomeShortcut.Friends, "Friends", Icons.Filled.People, SectionTint.Recent),
        ShortcutTile(HomeShortcut.Notifications, "Inbox", Icons.Filled.Notifications, SectionTint.Playlists),
    )
    Column(
        Modifier.fillMaxWidth().padding(horizontal = 16.dp, vertical = 8.dp),
        verticalArrangement = Arrangement.spacedBy(10.dp),
    ) {
        tiles.chunked(3).forEach { row ->
            Row(horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                row.forEach { tile ->
                    LumiCard(Modifier.weight(1f), onClick = { onShortcut(tile.shortcut) }) {
                        Row(
                            Modifier.padding(horizontal = 12.dp, vertical = 12.dp),
                            verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.spacedBy(8.dp),
                        ) {
                            Box(
                                Modifier.size(28.dp).clip(RoundedCornerShape(8.dp)).background(tile.tint.copy(alpha = 0.18f)),
                                contentAlignment = Alignment.Center,
                            ) {
                                Icon(tile.icon, contentDescription = null, tint = tile.tint, modifier = Modifier.size(16.dp))
                            }
                            OneLine(tile.label, style = MaterialTheme.typography.labelLarge)
                        }
                    }
                }
            }
        }
    }
}

@Composable
private fun DailyPickCard(pick: StreamTrackDto, reason: String?, onPlay: () -> Unit) {
    val palette = LocalLumiPalette.current
    Column {
        IconSectionHeader(Icons.Filled.AutoAwesome, "Aria's daily pick", tint = SectionTint.Offline)
        Box(
            Modifier
                .fillMaxWidth()
                .padding(horizontal = 16.dp)
                .clip(MaterialTheme.shapes.large)
                .background(
                    Brush.linearGradient(listOf(palette.accent.copy(alpha = 0.35f), palette.elevatedSurface))
                )
                .clickable(onClick = onPlay)
                .padding(16.dp)
        ) {
            Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(14.dp)) {
                Artwork(
                    model = pick.thumbnailUrl?.takeIf { it.isNotBlank() },
                    fallbackKey = "${pick.source}:${pick.id}",
                    size = 76.dp,
                    corner = 14.dp,
                )
                Column(Modifier.weight(1f)) {
                    OneLine(pick.title, style = MaterialTheme.typography.titleSmall)
                    OneLine(
                        streamSubtitle(pick),
                        style = MaterialTheme.typography.labelSmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                    reason?.let {
                        Spacer(Modifier.height(6.dp))
                        Text(
                            "“$it”",
                            style = MaterialTheme.typography.bodySmall,
                            maxLines = 3,
                            overflow = TextOverflow.Ellipsis,
                        )
                    }
                }
                Box(
                    Modifier.size(42.dp).clip(CircleShape).background(palette.accent),
                    contentAlignment = Alignment.Center,
                ) {
                    Icon(Icons.Filled.PlayArrow, contentDescription = "Play", tint = Color.White)
                }
            }
        }
    }
}

@Composable
private fun PlayShuffleRow(subtitle: String, onPlay: () -> Unit, onShuffle: () -> Unit) {
    val palette = LocalLumiPalette.current
    Row(
        Modifier.fillMaxWidth().padding(horizontal = 20.dp, vertical = 2.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Text(
            subtitle,
            style = MaterialTheme.typography.labelSmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            modifier = Modifier.weight(1f),
        )
        Row(
            Modifier.clip(CircleShape).background(palette.accent).clickable(onClick = onPlay)
                .padding(horizontal = 12.dp, vertical = 6.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Icon(Icons.Filled.PlayArrow, contentDescription = null, tint = Color.White, modifier = Modifier.size(16.dp))
            Text(" Play", style = MaterialTheme.typography.labelMedium, color = Color.White)
        }
        Spacer(Modifier.width(8.dp))
        Box(
            Modifier.size(30.dp).clip(CircleShape).background(palette.elevatedSurface).clickable(onClick = onShuffle),
            contentAlignment = Alignment.Center,
        ) {
            Icon(Icons.Filled.Shuffle, contentDescription = "Shuffle", modifier = Modifier.size(16.dp))
        }
    }
}

@Composable
private fun StreamShelf(tracks: List<StreamTrackDto>, onPlay: (List<StreamTrackDto>, Int) -> Unit) {
    LazyRow(contentPadding = PaddingValues(horizontal = 14.dp)) {
        itemsIndexed(tracks, key = { i, t -> "${t.source}:${t.id}#$i" }) { index, track ->
            ShelfCard(
                title = track.title,
                subtitle = trackSubtitle(track.title, track.artist, null),
                artworkModel = track.thumbnailUrl?.takeIf { it.isNotBlank() },
                fallbackKey = "${track.source}:${track.id}",
                onClick = { onPlay(tracks, index) },
            )
        }
    }
}

@Composable
private fun EpisodeProgressRow(episode: EpisodeProgressDto, onClick: () -> Unit) {
    val palette = LocalLumiPalette.current
    val fraction = if (episode.durationSeconds > 0) (episode.positionSeconds / episode.durationSeconds).toFloat().coerceIn(0f, 1f) else 0f
    val remaining = (episode.durationSeconds - episode.positionSeconds).toInt()
    Row(
        Modifier.fillMaxWidth().clickable(onClick = onClick).padding(horizontal = 20.dp, vertical = 8.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(12.dp),
    ) {
        Artwork(model = null, fallbackKey = episode.feedUrl, size = 44.dp, corner = 10.dp)
        Column(Modifier.weight(1f)) {
            OneLine(episode.title ?: "Episode", style = MaterialTheme.typography.bodyMedium)
            Spacer(Modifier.height(5.dp))
            Box(Modifier.fillMaxWidth().height(3.dp).clip(CircleShape).background(palette.hairline)) {
                Box(Modifier.fillMaxWidth(fraction).height(3.dp).clip(CircleShape).background(palette.accent))
            }
        }
        if (remaining > 0) {
            Text(
                "${(remaining + 59) / 60}m left",
                style = MaterialTheme.typography.labelSmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
        }
    }
}

@Composable
private fun TrendingRow(rank: Int, track: CommunityTrackDto, onClick: () -> Unit) {
    Row(
        Modifier.fillMaxWidth().clickable(onClick = onClick).padding(horizontal = 20.dp, vertical = 9.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(14.dp),
    ) {
        Text(
            "$rank",
            style = MaterialTheme.typography.titleMedium,
            color = LocalLumiPalette.current.accent,
            modifier = Modifier.width(22.dp),
        )
        Column(Modifier.weight(1f)) {
            OneLine(track.title, style = MaterialTheme.typography.bodyMedium)
            OneLine(
                trackSubtitle(track.title, track.artist, null),
                style = MaterialTheme.typography.labelSmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
        }
        Text(
            "${track.listenerCount} listener${if (track.listenerCount == 1) "" else "s"}",
            style = MaterialTheme.typography.labelSmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
        )
    }
}

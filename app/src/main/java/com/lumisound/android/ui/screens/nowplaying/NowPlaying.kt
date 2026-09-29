package com.lumisound.android.ui.screens.nowplaying

import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Bedtime
import androidx.compose.material.icons.filled.Favorite
import androidx.compose.material.icons.filled.Lyrics
import androidx.compose.material.icons.filled.FavoriteBorder
import androidx.compose.material.icons.filled.Pause
import androidx.compose.material.icons.filled.PlayArrow
import androidx.compose.material.icons.filled.QueueMusic
import androidx.compose.material.icons.filled.Repeat
import androidx.compose.material.icons.filled.RepeatOne
import androidx.compose.material.icons.filled.Shuffle
import androidx.compose.material.icons.filled.SkipNext
import androidx.compose.material.icons.filled.SkipPrevious
import androidx.compose.material.icons.filled.Speed
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.Slider
import androidx.compose.material3.SliderDefaults
import androidx.compose.material3.Text
import androidx.compose.material3.rememberModalBottomSheetState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.produceState
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.blur
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import coil3.compose.AsyncImage
import com.lumisound.android.AppContainer
import com.lumisound.android.lyrics.LrcParser
import com.lumisound.android.lyrics.LyricsResult
import com.lumisound.android.playback.PlaybackUiState
import com.lumisound.android.ui.components.Artwork
import com.lumisound.android.ui.components.FallbackArt
import com.lumisound.android.ui.components.OneLine
import com.lumisound.android.ui.components.trackSubtitle
import com.lumisound.android.ui.theme.LocalLumiPalette

/**
 * The bar above the tab bar. Carries its own progress line, because a mini player with
 * no sense of position is just a label with two buttons.
 */
@Composable
fun MiniPlayer(
    state: PlaybackUiState,
    onToggle: () -> Unit,
    onNext: () -> Unit,
    onExpand: () -> Unit,
    onFavorite: () -> Unit = {},
) {
    val palette = LocalLumiPalette.current
    val progress by animateFloatAsState(
        if (state.durationMs > 0) (state.positionMs.toFloat() / state.durationMs).coerceIn(0f, 1f) else 0f,
        label = "miniProgress",
    )
    val subtitle = trackSubtitle(state.title.orEmpty(), state.artist, null)

    Column(
        Modifier
            .fillMaxWidth()
            .padding(horizontal = 10.dp, vertical = 6.dp)
            .clip(RoundedCornerShape(18.dp))
            .background(palette.elevatedSurface)
            .clickable(onClick = onExpand)
    ) {
        Row(
            Modifier.fillMaxWidth().padding(start = 10.dp, end = 12.dp, top = 8.dp, bottom = 6.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(11.dp),
        ) {
            Artwork(
                model = state.artworkUrl,
                fallbackKey = state.serverPath ?: state.title.orEmpty(),
                size = 44.dp,
                corner = 11.dp,
            )
            Column(Modifier.weight(1f)) {
                OneLine(state.title ?: "Nothing playing", style = MaterialTheme.typography.titleSmall)
                OneLine(
                    subtitle,
                    style = MaterialTheme.typography.labelSmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }
            IconButton(onClick = onFavorite, modifier = Modifier.size(34.dp)) {
                Icon(
                    if (state.isFavorite) Icons.Filled.Favorite else Icons.Filled.FavoriteBorder,
                    contentDescription = "Favorite",
                    tint = if (state.isFavorite) palette.accent else MaterialTheme.colorScheme.onSurfaceVariant,
                    modifier = Modifier.size(19.dp),
                )
            }
            // The one filled control on the bar, as on iOS: everything else is an outline.
            Box(
                Modifier
                    .size(38.dp)
                    .clip(CircleShape)
                    .background(palette.accent)
                    .clickable(onClick = onToggle),
                contentAlignment = Alignment.Center,
            ) {
                Icon(
                    if (state.isPlaying) Icons.Filled.Pause else Icons.Filled.PlayArrow,
                    contentDescription = if (state.isPlaying) "Pause" else "Play",
                    tint = Color.White,
                    modifier = Modifier.size(21.dp),
                )
            }
            IconButton(onClick = onNext, modifier = Modifier.size(34.dp)) {
                Icon(Icons.Filled.SkipNext, contentDescription = "Next", modifier = Modifier.size(22.dp))
            }
        }
        Box(
            Modifier
                .padding(horizontal = 12.dp)
                .fillMaxWidth()
                .height(3.dp)
                .clip(CircleShape)
                .background(palette.hairline)
        ) {
            Box(
                Modifier
                    .fillMaxWidth(progress)
                    .fillMaxHeight()
                    .clip(CircleShape)
                    .background(palette.accent)
            )
        }
        Spacer(Modifier.height(8.dp))
    }
}

/**
 * Full-screen playback. The artwork is used twice: once blurred as the backdrop and once
 * sharp in the middle, so the screen takes its colour from whatever is playing instead of
 * being the same dark panel for every track.
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun NowPlayingSheet(container: AppContainer, onOpenQueue: () -> Unit, onDismiss: () -> Unit) {
    val state by container.player.state.collectAsStateWithLifecycle()
    val palette = LocalLumiPalette.current
    val sheetState = rememberModalBottomSheetState(skipPartiallyExpanded = true)
    var showLyrics by rememberSaveable { mutableStateOf(false) }
    val title = state.title
    // Only fetched once the panel is open, and never for an episode -- podcasts have none.
    val lyrics by produceState<LyricsResult?>(null, title, state.artist, showLyrics) {
        value = null
        if (showLyrics && title != null && state.podcastFeedUrl == null) {
            value = container.lyrics.lyricsFor(title, state.artist, state.durationMs)
        }
    }

    ModalBottomSheet(
        onDismissRequest = onDismiss,
        sheetState = sheetState,
        containerColor = palette.pageBottom,
        dragHandle = null,
    ) {
        NowPlayingContent(
            state = state,
            onSeek = container.player::seekTo,
            onToggle = container.player::togglePlayPause,
            onPrevious = { container.player.previous() },
            onNext = { container.player.next() },
            onShuffle = { container.player.setShuffle(!state.shuffleEnabled) },
            onRepeat = container.player::cycleRepeatMode,
            onSpeed = { container.player.setSpeed(it) },
            onOpenQueue = onOpenQueue,
            showLyrics = showLyrics,
            lyrics = lyrics,
            onToggleLyrics = { showLyrics = !showLyrics },
            onSleepTimer = { choice ->
                when (choice) {
                    null -> container.player.cancelSleepTimer()
                    SLEEP_END_OF_TRACK -> container.player.setSleepTimer(null, endOfTrack = true)
                    else -> container.player.setSleepTimer(choice)
                }
            },
        )
    }
}

/** Sleep-timer choices in minutes; [SLEEP_END_OF_TRACK] stops when the current track does. */
val SLEEP_CHOICES = listOf(15, 30, 45, 60, 90)
const val SLEEP_END_OF_TRACK = -1

/**
 * Everything the Now Playing sheet draws, with no container behind it, so it can be
 * rendered from a test with invented state -- including the states that are awkward to
 * reach by hand, like a track whose duration the player never worked out.
 */
@Composable
fun NowPlayingContent(
    state: PlaybackUiState,
    onSeek: (Long) -> Unit,
    onToggle: () -> Unit,
    onPrevious: () -> Unit,
    onNext: () -> Unit,
    onShuffle: () -> Unit,
    onRepeat: () -> Unit,
    onSpeed: (Float) -> Unit,
    onOpenQueue: () -> Unit,
    showLyrics: Boolean = false,
    /** Null while loading (or while the panel is closed). */
    lyrics: LyricsResult? = null,
    onToggleLyrics: () -> Unit = {},
    /** Minutes, [SLEEP_END_OF_TRACK], or null to cancel. */
    onSleepTimer: (Int?) -> Unit = {},
    nowMs: Long = System.currentTimeMillis(),
) {
    val palette = LocalLumiPalette.current
    var sleepMenu by remember { mutableStateOf(false) }
    run {
        Box(Modifier.fillMaxWidth()) {
            // The backdrop uses whatever the row uses -- real artwork when the track has it,
            // otherwise the same generated gradient -- so this screen always takes its colour
            // from the track. It matches the content's own height rather than a fixed block,
            // which previously ended in a hard horizontal edge partway down the sheet.
            Box(Modifier.matchParentSize()) {
                FallbackArt(
                    key = state.serverPath ?: state.title.orEmpty(),
                    modifier = Modifier.fillMaxSize().blur(60.dp),
                )
                state.artworkUrl?.let { artwork ->
                    AsyncImage(
                        model = artwork,
                        contentDescription = null,
                        contentScale = ContentScale.Crop,
                        // Modifier.blur is a real RenderEffect from API 31 and a no-op below
                        // it; the scrim over the top keeps both cases looking deliberate.
                        modifier = Modifier.fillMaxSize().blur(60.dp),
                    )
                }
                Box(
                    Modifier
                        .fillMaxSize()
                        .background(
                            Brush.verticalGradient(
                                0f to Color.Black.copy(alpha = 0.30f),
                                0.45f to palette.pageBottom.copy(alpha = 0.86f),
                                1f to palette.pageBottom,
                            )
                        )
                )
            }

            Column(
                Modifier
                    .fillMaxWidth()
                    // Scrollable on purpose: artwork plus transport plus the secondary row
                    // is taller than a short screen, and a control that cannot be reached
                    // is worse than one that needs a nudge.
                    .verticalScroll(rememberScrollState())
                    .padding(horizontal = 26.dp, vertical = 18.dp),
                horizontalAlignment = Alignment.CenterHorizontally,
            ) {
                Box(
                    Modifier
                        .size(width = 38.dp, height = 4.dp)
                        .clip(CircleShape)
                        .background(Color.White.copy(alpha = 0.25f))
                )
                Spacer(Modifier.height(22.dp))

                if (showLyrics) {
                    LyricsPanel(lyrics, state.positionMs, onSeek)
                } else {
                    Artwork(
                        model = state.artworkUrl,
                        fallbackKey = state.serverPath ?: state.title.orEmpty(),
                        size = 232.dp,
                        corner = 20.dp,
                        modifier = Modifier.aspectRatio(1f),
                    )
                }

                Spacer(Modifier.height(22.dp))
                Text(
                    state.title ?: "Nothing playing",
                    style = MaterialTheme.typography.headlineSmall,
                    textAlign = TextAlign.Center,
                    maxLines = 2,
                    overflow = TextOverflow.Ellipsis,
                    modifier = Modifier.fillMaxWidth(),
                )
                Spacer(Modifier.height(4.dp))
                Text(
                    trackSubtitle(state.title.orEmpty(), state.artist, null),
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    textAlign = TextAlign.Center,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                    modifier = Modifier.fillMaxWidth(),
                )

                Spacer(Modifier.height(18.dp))
                val known = state.durationMs > 0
                val duration = state.durationMs.coerceAtLeast(1)
                Slider(
                    // An unknown duration shows an empty track rather than a full one: a bar
                    // pinned to the end while the track is eleven seconds in says something
                    // false about the track instead of admitting what is not known.
                    value = if (known) (state.positionMs.toFloat() / duration).coerceIn(0f, 1f) else 0f,
                    enabled = known,
                    onValueChange = { onSeek((it * duration).toLong()) },
                    colors = SliderDefaults.colors(
                        thumbColor = palette.accent,
                        activeTrackColor = palette.accent,
                        inactiveTrackColor = palette.hairline,
                    ),
                )
                Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
                    Text(
                        state.positionMs.asClock(),
                        style = MaterialTheme.typography.labelSmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                    Text(
                        if (known) state.durationMs.asClock() else "--:--",
                        style = MaterialTheme.typography.labelSmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                }

                Spacer(Modifier.height(10.dp))
                Row(
                    Modifier.fillMaxWidth(),
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.SpaceEvenly,
                ) {
                    IconButton(onClick = onShuffle) {
                        Icon(
                            Icons.Filled.Shuffle,
                            contentDescription = "Shuffle",
                            tint = if (state.shuffleEnabled) palette.accent else MaterialTheme.colorScheme.onSurfaceVariant,
                        )
                    }
                    IconButton(onClick = onPrevious) {
                        Icon(Icons.Filled.SkipPrevious, contentDescription = "Previous", modifier = Modifier.size(38.dp))
                    }
                    Box(
                        Modifier
                            .size(68.dp)
                            .clip(CircleShape)
                            .background(palette.accent)
                            .clickable(onClick = onToggle),
                        contentAlignment = Alignment.Center,
                    ) {
                        Icon(
                            if (state.isPlaying) Icons.Filled.Pause else Icons.Filled.PlayArrow,
                            contentDescription = if (state.isPlaying) "Pause" else "Play",
                            tint = Color.White,
                            modifier = Modifier.size(34.dp),
                        )
                    }
                    IconButton(onClick = onNext) {
                        Icon(Icons.Filled.SkipNext, contentDescription = "Next", modifier = Modifier.size(38.dp))
                    }
                    IconButton(onClick = onRepeat) {
                        Icon(
                            // REPEAT_MODE_ONE == 1; the icon says which mode is on.
                            if (state.repeatMode == 1) Icons.Filled.RepeatOne else Icons.Filled.Repeat,
                            contentDescription = "Repeat",
                            tint = if (state.repeatMode != 0) palette.accent else MaterialTheme.colorScheme.onSurfaceVariant,
                        )
                    }
                }

                Spacer(Modifier.height(6.dp))
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(10.dp),
                ) {
                    IconButton(onClick = {
                        // Cycles the usual four rather than offering a slider, which is more
                        // precision than anyone wants while listening.
                        val next = when {
                            state.speed < 0.9f -> 1.0f
                            state.speed < 1.1f -> 1.25f
                            state.speed < 1.3f -> 1.5f
                            state.speed < 1.6f -> 0.75f
                            else -> 1.0f
                        }
                        onSpeed(next)
                    }) {
                        Icon(
                            Icons.Filled.Speed,
                            contentDescription = "Speed",
                            tint = if (state.speed != 1f) palette.accent else MaterialTheme.colorScheme.onSurfaceVariant,
                        )
                    }
                    if (state.speed != 1f) {
                        Text("${state.speed}x", style = MaterialTheme.typography.labelSmall, color = palette.accent)
                    }
                    IconButton(onClick = onOpenQueue) {
                        Icon(
                            Icons.Filled.QueueMusic,
                            contentDescription = "Queue",
                            tint = MaterialTheme.colorScheme.onSurfaceVariant,
                        )
                    }
                    Text(
                        "${state.queueIndex + 1} of ${state.queueSize}",
                        style = MaterialTheme.typography.labelSmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                    IconButton(onClick = onToggleLyrics) {
                        Icon(
                            Icons.Filled.Lyrics,
                            contentDescription = if (showLyrics) "Hide lyrics" else "Show lyrics",
                            tint = if (showLyrics) palette.accent else MaterialTheme.colorScheme.onSurfaceVariant,
                        )
                    }
                    val timerOn = state.sleepAtMs != null || state.sleepAtEndOfTrack
                    Box {
                        IconButton(onClick = { sleepMenu = true }) {
                            Icon(
                                Icons.Filled.Bedtime,
                                contentDescription = "Sleep timer",
                                tint = if (timerOn) palette.accent else MaterialTheme.colorScheme.onSurfaceVariant,
                            )
                        }
                        DropdownMenu(expanded = sleepMenu, onDismissRequest = { sleepMenu = false }) {
                            SLEEP_CHOICES.forEach { minutes ->
                                DropdownMenuItem(
                                    text = { Text("$minutes minutes") },
                                    onClick = { sleepMenu = false; onSleepTimer(minutes) },
                                )
                            }
                            DropdownMenuItem(
                                text = { Text("End of this track") },
                                onClick = { sleepMenu = false; onSleepTimer(SLEEP_END_OF_TRACK) },
                            )
                            if (timerOn) {
                                DropdownMenuItem(
                                    text = { Text("Turn off timer") },
                                    onClick = { sleepMenu = false; onSleepTimer(null) },
                                )
                            }
                        }
                    }
                }

                sleepLabel(state, nowMs)?.let {
                    Text(it, style = MaterialTheme.typography.labelSmall, color = palette.accent)
                }

                state.playbackError?.let {
                    Spacer(Modifier.height(10.dp))
                    Text(it, style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.error)
                }
                Spacer(Modifier.height(24.dp))
            }
        }
    }
}

/** "Sleeping in 23 min" / "Stopping after this track", or null when no timer is set. */
fun sleepLabel(state: PlaybackUiState, nowMs: Long): String? = when {
    state.sleepAtEndOfTrack -> "Stopping after this track"
    state.sleepAtMs != null -> {
        val minutes = ((state.sleepAtMs - nowMs + 59_999) / 60_000).coerceAtLeast(1)
        "Sleeping in $minutes min"
    }
    else -> null
}

/**
 * Synced lyrics with the current line lit and kept in view, or plain lyrics scrolling
 * freely. Tapping a synced line seeks to it. Sized like the artwork it replaces, so the
 * transport below never moves when the panel is toggled.
 */
@Composable
private fun LyricsPanel(lyrics: LyricsResult?, positionMs: Long, onSeek: (Long) -> Unit) {
    val palette = LocalLumiPalette.current
    Box(
        Modifier
            .fillMaxWidth()
            .height(300.dp)
            .clip(RoundedCornerShape(20.dp))
            .background(Color.Black.copy(alpha = 0.22f)),
        contentAlignment = Alignment.Center,
    ) {
        when (lyrics) {
            null -> CircularProgressIndicator(color = palette.accent, strokeWidth = 2.dp, modifier = Modifier.size(24.dp))
            LyricsResult.None -> Text(
                "No lyrics for this track.",
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
            is LyricsResult.Plain -> Text(
                lyrics.text,
                style = MaterialTheme.typography.bodyLarge,
                textAlign = TextAlign.Center,
                modifier = Modifier.fillMaxSize().verticalScroll(rememberScrollState()).padding(18.dp),
            )
            is LyricsResult.Synced -> {
                val active = LrcParser.activeIndex(lyrics.lines, positionMs)
                val listState = rememberLazyListState()
                LaunchedEffect(active) {
                    // Keep the live line about a third of the way down, with context above it.
                    if (active >= 0) listState.animateScrollToItem((active - 2).coerceAtLeast(0))
                }
                LazyColumn(
                    state = listState,
                    modifier = Modifier.fillMaxSize(),
                    contentPadding = PaddingValues(horizontal = 18.dp, vertical = 16.dp),
                    verticalArrangement = Arrangement.spacedBy(10.dp),
                ) {
                    itemsIndexed(lyrics.lines) { index, line ->
                        val isActive = index == active
                        Text(
                            line.text,
                            style = if (isActive) MaterialTheme.typography.titleLarge else MaterialTheme.typography.titleMedium,
                            color = when {
                                isActive -> Color.White
                                index < active -> MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.55f)
                                else -> MaterialTheme.colorScheme.onSurfaceVariant
                            },
                            modifier = Modifier.fillMaxWidth().clickable { onSeek(line.timeMs) },
                        )
                    }
                }
            }
        }
    }
}

private fun Long.asClock(): String {
    if (this <= 0) return "0:00"
    val seconds = this / 1000
    return "%d:%02d".format(seconds / 60, seconds % 60)
}

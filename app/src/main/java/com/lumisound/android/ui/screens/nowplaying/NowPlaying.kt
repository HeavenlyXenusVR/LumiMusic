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
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Pause
import androidx.compose.material.icons.filled.PlayArrow
import androidx.compose.material.icons.filled.QueueMusic
import androidx.compose.material.icons.filled.Repeat
import androidx.compose.material.icons.filled.RepeatOne
import androidx.compose.material.icons.filled.Shuffle
import androidx.compose.material.icons.filled.SkipNext
import androidx.compose.material.icons.filled.SkipPrevious
import androidx.compose.material.icons.filled.Speed
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
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.blur
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import coil3.compose.AsyncImage
import com.lumisound.android.AppContainer
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
            .background(palette.elevatedSurface)
            .clickable(onClick = onExpand)
    ) {
        Box(Modifier.fillMaxWidth().height(2.dp).background(palette.hairline)) {
            Box(
                Modifier
                    .fillMaxWidth(progress)
                    .fillMaxHeight()
                    .background(palette.accent)
            )
        }
        Row(
            Modifier.fillMaxWidth().padding(horizontal = 12.dp, vertical = 8.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(12.dp),
        ) {
            Artwork(
                model = state.artworkUrl,
                fallbackKey = state.serverPath ?: state.title.orEmpty(),
                size = 42.dp,
                corner = 9.dp,
            )
            Column(Modifier.weight(1f)) {
                OneLine(state.title ?: "Nothing playing", style = MaterialTheme.typography.titleSmall)
                OneLine(
                    subtitle,
                    style = MaterialTheme.typography.labelSmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }
            IconButton(onClick = onToggle) {
                Icon(
                    if (state.isPlaying) Icons.Filled.Pause else Icons.Filled.PlayArrow,
                    contentDescription = if (state.isPlaying) "Pause" else "Play",
                )
            }
            IconButton(onClick = onNext) {
                Icon(Icons.Filled.SkipNext, contentDescription = "Next")
            }
        }
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

    ModalBottomSheet(
        onDismissRequest = onDismiss,
        sheetState = sheetState,
        containerColor = palette.pageBottom,
        dragHandle = null,
    ) {
        Box(Modifier.fillMaxWidth()) {
            // The backdrop uses whatever the row uses: real artwork when the track has it,
            // otherwise the same generated gradient, so this screen always takes its colour
            // from the track rather than falling back to a flat panel.
            Box(Modifier.fillMaxWidth().height(420.dp)) {
                FallbackArt(
                    key = state.serverPath ?: state.title.orEmpty(),
                    modifier = Modifier.fillMaxSize().blur(52.dp),
                )
                state.artworkUrl?.let { artwork ->
                    AsyncImage(
                        model = artwork,
                        contentDescription = null,
                        contentScale = ContentScale.Crop,
                        modifier = Modifier
                            .fillMaxSize()
                            // Modifier.blur is a real RenderEffect from API 31 and a no-op
                            // below it; the scrim below keeps both cases looking deliberate.
                            .blur(52.dp),
                    )
                }
            }
            Box(
                Modifier
                    .fillMaxWidth()
                    .height(420.dp)
                    .background(
                        Brush.verticalGradient(
                            listOf(Color.Black.copy(alpha = 0.35f), palette.pageBottom)
                        )
                    )
            )

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

                Artwork(
                    model = state.artworkUrl,
                    fallbackKey = state.serverPath ?: state.title.orEmpty(),
                    size = 268.dp,
                    corner = 20.dp,
                    modifier = Modifier.aspectRatio(1f),
                )

                Spacer(Modifier.height(26.dp))
                OneLine(
                    state.title ?: "Nothing playing",
                    style = MaterialTheme.typography.headlineSmall,
                    modifier = Modifier.fillMaxWidth(),
                )
                Spacer(Modifier.height(4.dp))
                OneLine(
                    trackSubtitle(state.title.orEmpty(), state.artist, null),
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
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
                    onValueChange = { container.player.seekTo((it * duration).toLong()) },
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
                    IconButton(onClick = { container.player.setShuffle(!state.shuffleEnabled) }) {
                        Icon(
                            Icons.Filled.Shuffle,
                            contentDescription = "Shuffle",
                            tint = if (state.shuffleEnabled) palette.accent else MaterialTheme.colorScheme.onSurfaceVariant,
                        )
                    }
                    IconButton(onClick = { container.player.previous() }) {
                        Icon(Icons.Filled.SkipPrevious, contentDescription = "Previous", modifier = Modifier.size(38.dp))
                    }
                    Box(
                        Modifier
                            .size(68.dp)
                            .clip(CircleShape)
                            .background(palette.accent)
                            .clickable(onClick = container.player::togglePlayPause),
                        contentAlignment = Alignment.Center,
                    ) {
                        Icon(
                            if (state.isPlaying) Icons.Filled.Pause else Icons.Filled.PlayArrow,
                            contentDescription = if (state.isPlaying) "Pause" else "Play",
                            tint = Color.White,
                            modifier = Modifier.size(34.dp),
                        )
                    }
                    IconButton(onClick = { container.player.next() }) {
                        Icon(Icons.Filled.SkipNext, contentDescription = "Next", modifier = Modifier.size(38.dp))
                    }
                    IconButton(onClick = container.player::cycleRepeatMode) {
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
                        container.player.setSpeed(next)
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

private fun Long.asClock(): String {
    if (this <= 0) return "0:00"
    val seconds = this / 1000
    return "%d:%02d".format(seconds / 60, seconds % 60)
}

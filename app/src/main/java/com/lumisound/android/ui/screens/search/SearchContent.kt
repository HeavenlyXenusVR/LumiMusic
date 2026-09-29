package com.lumisound.android.ui.screens.search

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.CloudQueue
import androidx.compose.material.icons.filled.History
import androidx.compose.material.icons.filled.MusicNote
import androidx.compose.material.icons.filled.NorthWest
import androidx.compose.material.icons.filled.PlayArrow
import androidx.compose.material.icons.filled.Radio
import androidx.compose.material.icons.filled.Search
import androidx.compose.material.icons.filled.Shuffle
import androidx.compose.material.icons.filled.TrendingUp
import androidx.compose.material.icons.filled.Waves
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.unit.dp
import com.lumisound.android.bridge.model.StreamTrackDto
import com.lumisound.android.data.db.CloudTrackEntity
import com.lumisound.android.ui.components.ChipRow
import com.lumisound.android.ui.components.EmptyState
import com.lumisound.android.ui.components.IconSectionHeader
import com.lumisound.android.ui.components.Loadable
import com.lumisound.android.ui.components.LoadableSection
import com.lumisound.android.ui.components.NavChip
import com.lumisound.android.ui.components.OneLine
import com.lumisound.android.ui.components.ScreenTitle
import com.lumisound.android.ui.components.SearchField
import com.lumisound.android.ui.components.StreamTrackActions
import com.lumisound.android.ui.components.StreamTrackRow
import com.lumisound.android.ui.components.TrackRow
import com.lumisound.android.ui.components.trackSubtitle
import com.lumisound.android.ui.screens.cloud.asClock
import com.lumisound.android.ui.theme.LocalLumiPalette
import com.lumisound.android.ui.theme.SectionTint

/** Where a search looks. The first two go through the bridge's yt-dlp catalogue. */
enum class SearchSource(val label: String, val bridgeSource: String?) {
    YouTube("YouTube", "youtube"),
    SoundCloud("SoundCloud", "soundcloud"),
    Library("Your cloud", null),
}

data class SearchUiState(
    val query: String = "",
    val source: SearchSource = SearchSource.YouTube,
    /** The query the current results are for; null before the first search. */
    val submitted: String? = null,
    val streamResults: Loadable<List<StreamTrackDto>> = Loadable.Ready(emptyList()),
    val libraryResults: List<CloudTrackEntity> = emptyList(),
    val suggestions: List<String> = emptyList(),
    val recent: List<String> = emptyList(),
    val trending: List<String> = emptyList(),
    val playingId: String? = null,
)

data class SearchCallbacks(
    val onQueryChange: (String) -> Unit = {},
    val onSubmit: (String) -> Unit = {},
    val onSourceChange: (SearchSource) -> Unit = {},
    val onPlayStream: (List<StreamTrackDto>, Int) -> Unit = { _, _ -> },
    val onShuffleStreams: (List<StreamTrackDto>) -> Unit = {},
    val streamActions: StreamTrackActions = StreamTrackActions(),
    val onPlayLibrary: (List<CloudTrackEntity>, Int) -> Unit = { _, _ -> },
    val libraryArtwork: (CloudTrackEntity) -> Any? = { null },
    val onClearRecent: () -> Unit = {},
    val onRetry: () -> Unit = {},
)

/**
 * Search, for the whole catalogue rather than just what is already in the library -- the
 * feature Lumisound is built around and the one this port was most missing.
 *
 * YouTube and SoundCloud results go through the bridge, which does the extraction and
 * re-streams the audio; "Your cloud" searches the local mirror instantly. Before a search,
 * the screen offers the device's recent queries and what everyone has been searching.
 */
@OptIn(ExperimentalLayoutApi::class)
@Composable
fun SearchContent(state: SearchUiState, callbacks: SearchCallbacks) {
    val palette = LocalLumiPalette.current
    Column(Modifier.fillMaxSize()) {
        ScreenTitle("Search", subtitle = "YouTube, SoundCloud and your cloud library", modifier = Modifier.padding(top = 12.dp))
        SearchField(
            value = state.query,
            onValueChange = callbacks.onQueryChange,
            placeholder = when (state.source) {
                SearchSource.Library -> "Songs, artists, albums in your cloud…"
                else -> "Search ${state.source.label}…"
            },
            onSubmit = { callbacks.onSubmit(state.query) },
        )
        ChipRow(
            SearchSource.entries.map { source ->
                NavChip(
                    label = source.label,
                    icon = when (source) {
                        SearchSource.YouTube -> Icons.Filled.PlayArrow
                        SearchSource.SoundCloud -> Icons.Filled.Waves
                        SearchSource.Library -> Icons.Filled.CloudQueue
                    },
                    selected = state.source == source,
                    onClick = { callbacks.onSourceChange(source) },
                )
            }
        )

        val showingResults = state.source == SearchSource.Library && state.query.isNotBlank() ||
            state.source != SearchSource.Library && state.submitted != null && state.submitted == state.query.trim()

        if (!showingResults) {
            LazyColumn(Modifier.fillMaxSize(), contentPadding = PaddingValues(bottom = 24.dp)) {
                if (state.suggestions.isNotEmpty() && state.query.isNotBlank()) {
                    items(state.suggestions, key = { "s-$it" }) { suggestion ->
                        QueryRow(Icons.Filled.Search, suggestion, onClick = { callbacks.onSubmit(suggestion) })
                    }
                }
                if (state.query.isBlank() && state.recent.isNotEmpty()) {
                    item {
                        IconSectionHeader(
                            Icons.Filled.History,
                            "Recent searches",
                            tint = SectionTint.Recent,
                        )
                    }
                    items(state.recent, key = { "r-$it" }) { query ->
                        QueryRow(Icons.Filled.History, query, onClick = { callbacks.onSubmit(query) })
                    }
                    item {
                        TextButton(onClick = callbacks.onClearRecent, modifier = Modifier.padding(start = 8.dp)) {
                            Text("Clear recent searches")
                        }
                    }
                }
                if (state.query.isBlank() && state.trending.isNotEmpty()) {
                    item { IconSectionHeader(Icons.Filled.TrendingUp, "Trending searches", tint = SectionTint.Playlists) }
                    item {
                        FlowRow(
                            Modifier.fillMaxWidth().padding(horizontal = 16.dp),
                            horizontalArrangement = Arrangement.spacedBy(8.dp),
                            verticalArrangement = Arrangement.spacedBy(8.dp),
                        ) {
                            state.trending.forEach { query ->
                                Box(
                                    Modifier
                                        .clip(CircleShape)
                                        .background(palette.elevatedSurface)
                                        .clickable { callbacks.onSubmit(query) }
                                        .padding(horizontal = 14.dp, vertical = 8.dp)
                                ) {
                                    Text(query, style = MaterialTheme.typography.labelLarge)
                                }
                            }
                        }
                    }
                }
                if (state.query.isBlank() && state.recent.isEmpty() && state.trending.isEmpty()) {
                    item {
                        EmptyState(
                            icon = Icons.Filled.MusicNote,
                            title = "Find anything",
                            message = "Search YouTube and SoundCloud through your bridge, and play it straight away — or queue it, or start a radio from it.",
                            modifier = Modifier.height(360.dp),
                        )
                    }
                }
            }
            return@Column
        }

        if (state.source == SearchSource.Library) {
            LibraryResults(state, callbacks)
            return@Column
        }

        LoadableSection(state.streamResults, onRetry = callbacks.onRetry) { results ->
            if (results.isEmpty()) {
                EmptyState(
                    icon = Icons.Filled.Search,
                    title = "No results",
                    message = "Nothing on ${state.source.label} matched “${state.submitted}”.",
                )
                return@LoadableSection
            }
            LazyColumn(Modifier.fillMaxSize(), contentPadding = PaddingValues(bottom = 24.dp)) {
                item {
                    Row(
                        Modifier.fillMaxWidth().padding(horizontal = 16.dp, vertical = 6.dp),
                        horizontalArrangement = Arrangement.spacedBy(10.dp),
                    ) {
                        Button(
                            onClick = { callbacks.onPlayStream(results, 0) },
                            shape = MaterialTheme.shapes.extraLarge,
                            modifier = Modifier.weight(1f).height(46.dp),
                        ) {
                            Icon(Icons.Filled.PlayArrow, contentDescription = null, modifier = Modifier.size(19.dp))
                            Text("  Play all")
                        }
                        Button(
                            onClick = { callbacks.onShuffleStreams(results) },
                            shape = MaterialTheme.shapes.extraLarge,
                            colors = ButtonDefaults.buttonColors(
                                containerColor = palette.elevatedSurface,
                                contentColor = MaterialTheme.colorScheme.onSurface,
                            ),
                            modifier = Modifier.weight(1f).height(46.dp),
                        ) {
                            Icon(Icons.Filled.Shuffle, contentDescription = null, modifier = Modifier.size(19.dp))
                            Text("  Shuffle")
                        }
                    }
                }
                if (state.source == SearchSource.YouTube) {
                    item {
                        Row(
                            Modifier.fillMaxWidth().padding(horizontal = 20.dp, vertical = 4.dp),
                            verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.spacedBy(6.dp),
                        ) {
                            Icon(Icons.Filled.Radio, contentDescription = null, tint = palette.accent, modifier = Modifier.size(14.dp))
                            Text(
                                "Tip: “Start radio” from a result's menu queues a mix built around it.",
                                style = MaterialTheme.typography.labelSmall,
                                color = MaterialTheme.colorScheme.onSurfaceVariant,
                            )
                        }
                    }
                }
                itemsIndexed(results, key = { i, t -> "${t.source}:${t.id}#$i" }) { index, track ->
                    StreamTrackRow(
                        track = track,
                        isPlaying = state.playingId == "${track.source}:${track.id}",
                        onClick = { callbacks.onPlayStream(results, index) },
                        actions = callbacks.streamActions,
                    )
                }
            }
        }
    }
}

@Composable
private fun LibraryResults(state: SearchUiState, callbacks: SearchCallbacks) {
    if (state.libraryResults.isEmpty()) {
        EmptyState(
            icon = Icons.Filled.CloudQueue,
            title = "Not in your cloud",
            message = "Nothing in your cloud library matches that. Try YouTube or SoundCloud instead.",
        )
        return
    }
    LazyColumn(Modifier.fillMaxSize(), contentPadding = PaddingValues(bottom = 24.dp)) {
        itemsIndexed(state.libraryResults, key = { _, t -> t.serverPath }) { index, track ->
            TrackRow(
                title = track.title,
                subtitle = trackSubtitle(track.title, track.artist, track.album),
                artworkModel = callbacks.libraryArtwork(track),
                fallbackKey = track.serverPath,
                duration = track.durationSeconds.asClock(),
                isPlaying = state.playingId == track.serverPath,
                isLocked = track.isLocked,
                onClick = { callbacks.onPlayLibrary(state.libraryResults, index) },
            )
        }
    }
}

@Composable
private fun QueryRow(icon: androidx.compose.ui.graphics.vector.ImageVector, text: String, onClick: () -> Unit) {
    Row(
        Modifier.fillMaxWidth().clickable(onClick = onClick).padding(horizontal = 20.dp, vertical = 12.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(14.dp),
    ) {
        Icon(icon, contentDescription = null, tint = MaterialTheme.colorScheme.onSurfaceVariant, modifier = Modifier.size(18.dp))
        OneLine(text, style = MaterialTheme.typography.bodyLarge, modifier = Modifier.weight(1f))
        Icon(Icons.Filled.NorthWest, contentDescription = null, tint = MaterialTheme.colorScheme.onSurfaceVariant, modifier = Modifier.size(16.dp))
    }
}

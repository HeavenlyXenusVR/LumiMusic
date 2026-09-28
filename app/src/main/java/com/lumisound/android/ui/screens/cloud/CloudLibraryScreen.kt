package com.lumisound.android.ui.screens.cloud

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.CloudQueue
import androidx.compose.material.icons.filled.PlayArrow
import androidx.compose.material.icons.filled.Search
import androidx.compose.material.icons.filled.Shuffle
import androidx.compose.material3.Button
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.OutlinedTextFieldDefaults
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.lumisound.android.AppContainer
import com.lumisound.android.bridge.BridgeUrls
import com.lumisound.android.data.db.CloudTrackEntity
import com.lumisound.android.playback.toPlayable
import com.lumisound.android.ui.components.EmptyState
import com.lumisound.android.ui.components.Pill
import com.lumisound.android.ui.components.SectionHeader
import com.lumisound.android.ui.components.ShelfCard
import com.lumisound.android.ui.components.TrackRow
import com.lumisound.android.ui.components.trackSubtitle
import com.lumisound.android.ui.theme.LocalLumiPalette
import kotlinx.coroutines.launch

/**
 * The account's cloud library.
 *
 * Structured rather than a bare list: a recently-added shelf across the top, then every
 * track. A flat 3500-row list is technically complete and useless as a way in, which is
 * exactly how the first build read.
 */
@Composable
fun CloudLibraryScreen(
    container: AppContainer,
    onOpenImport: () -> Unit,
    onAddToPlaylist: (title: String, artist: String?, album: String?, songId: String, durationSeconds: Int) -> Unit,
) {
    val scope = rememberCoroutineScope()
    val palette = LocalLumiPalette.current
    var query by remember { mutableStateOf("") }
    var menuFor by remember { mutableStateOf<String?>(null) }

    val tracks by remember(query) {
        if (query.isBlank()) container.database.cloudTracks().observeAll()
        else container.database.cloudTracks().search(query)
    }.collectAsStateWithLifecycle(initialValue = emptyList())
    val recent by remember { container.database.cloudTracks().observeRecentlyAdded(20) }
        .collectAsStateWithLifecycle(initialValue = emptyList())
    val favoriteIds by container.database.favorites().observeIds()
        .collectAsStateWithLifecycle(initialValue = emptyList())
    val downloadedPaths by container.database.downloads().observePaths()
        .collectAsStateWithLifecycle(initialValue = emptyList())
    val playback by container.player.state.collectAsStateWithLifecycle()

    /** Substitutes an offline copy wherever one exists, so a downloaded track never re-streams. */
    suspend fun playables(list: List<CloudTrackEntity>) = list.map { track ->
        track.toPlayable(container.config.baseUrl, container.downloads.isDownloaded(track.serverPath))
    }

    fun play(list: List<CloudTrackEntity>, index: Int, shuffle: Boolean = false) {
        scope.launch {
            container.player.setShuffle(shuffle)
            container.player.play(playables(list), index)
        }
    }

    if (tracks.isEmpty() && query.isBlank()) {
        EmptyState(
            icon = Icons.Filled.CloudQueue,
            title = "Your cloud library is empty here",
            message = "Import this account's cloud data to pull its tracks, favorites and playlists onto this device.",
            action = { Button(onClick = onOpenImport) { Text("Import cloud data") } },
        )
        return
    }

    LazyColumn(Modifier.fillMaxSize(), contentPadding = PaddingValues(bottom = 24.dp)) {
        item {
            Column(Modifier.padding(horizontal = 20.dp, vertical = 12.dp)) {
                Text("Cloud", style = MaterialTheme.typography.displaySmall)
                Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    Pill("${tracks.size} tracks")
                    if (downloadedPaths.isNotEmpty()) Pill("${downloadedPaths.size} offline")
                }
            }
        }

        item {
            OutlinedTextField(
                value = query,
                onValueChange = { query = it },
                placeholder = { Text("Search titles, artists, albums") },
                leadingIcon = { Icon(Icons.Filled.Search, contentDescription = null) },
                singleLine = true,
                shape = MaterialTheme.shapes.large,
                colors = OutlinedTextFieldDefaults.colors(
                    unfocusedBorderColor = palette.hairline,
                    focusedBorderColor = palette.accent,
                ),
                modifier = Modifier.fillMaxWidth().padding(horizontal = 16.dp, vertical = 4.dp),
            )
        }

        if (query.isBlank()) {
            item {
                Row(
                    Modifier.fillMaxWidth().padding(horizontal = 16.dp, vertical = 10.dp),
                    horizontalArrangement = Arrangement.spacedBy(10.dp),
                ) {
                    Button(
                        onClick = { play(tracks, 0) },
                        modifier = Modifier.weight(1f),
                    ) {
                        Icon(Icons.Filled.PlayArrow, contentDescription = null, modifier = Modifier.size(18.dp))
                        Text("  Play all")
                    }
                    Button(
                        onClick = { play(tracks, (tracks.indices).randomOrNull() ?: 0, shuffle = true) },
                        modifier = Modifier.weight(1f),
                    ) {
                        Icon(Icons.Filled.Shuffle, contentDescription = null, modifier = Modifier.size(18.dp))
                        Text("  Shuffle")
                    }
                }
            }

            if (recent.isNotEmpty()) {
                item { SectionHeader("Recently added", subtitle = "Newest uploads to your cloud storage") }
                item {
                    LazyRow(contentPadding = PaddingValues(horizontal = 14.dp)) {
                        items(recent, key = { it.serverPath }) { track ->
                            ShelfCard(
                                title = track.title,
                                subtitle = trackSubtitle(track.title, track.artist, track.album),
                                artworkModel = track.artworkModel(container.config.baseUrl),
                                fallbackKey = track.serverPath,
                                onClick = { play(recent, recent.indexOf(track)) },
                            )
                        }
                    }
                }
            }

            item { SectionHeader("All tracks", subtitle = "${tracks.size} in your cloud storage") }
        }

        items(tracks, key = { it.serverPath }) { track ->
            Box {
                TrackRow(
                    title = track.title,
                    subtitle = trackSubtitle(track.title, track.artist, track.album),
                    artworkModel = track.artworkModel(container.config.baseUrl),
                    fallbackKey = track.serverPath,
                    duration = track.durationSeconds.asClock(),
                    isPlaying = playback.serverPath == track.serverPath,
                    isFavorite = track.serverPath in favoriteIds,
                    isDownloaded = track.serverPath in downloadedPaths,
                    isLocked = track.isLocked,
                    onClick = { play(tracks, tracks.indexOfFirst { it.serverPath == track.serverPath }) },
                    onMenu = { menuFor = track.serverPath },
                )
                DropdownMenu(
                    expanded = menuFor == track.serverPath,
                    onDismissRequest = { menuFor = null },
                ) {
                    val isDownloaded = track.serverPath in downloadedPaths
                    val isFavorite = track.serverPath in favoriteIds
                    DropdownMenuItem(
                        text = { Text("Play next") },
                        onClick = {
                            menuFor = null
                            scope.launch { container.player.playNext(playables(listOf(track)).first()) }
                        },
                    )
                    DropdownMenuItem(
                        text = { Text("Add to queue") },
                        onClick = {
                            menuFor = null
                            scope.launch { container.player.enqueue(playables(listOf(track))) }
                        },
                    )
                    DropdownMenuItem(
                        text = { Text(if (isFavorite) "Remove favorite" else "Add favorite") },
                        onClick = {
                            menuFor = null
                            scope.launch {
                                container.libraryRepository.toggleFavorite(
                                    track.serverPath, track.title, track.artist, track.album,
                                )
                            }
                        },
                    )
                    DropdownMenuItem(
                        text = { Text("Add to playlist…") },
                        onClick = {
                            menuFor = null
                            onAddToPlaylist(
                                track.title, track.artist, track.album, track.serverPath, track.durationSeconds.toInt(),
                            )
                        },
                    )
                    DropdownMenuItem(
                        text = { Text(if (isDownloaded) "Remove download" else "Download for offline") },
                        onClick = {
                            menuFor = null
                            if (isDownloaded) scope.launch { container.downloads.remove(track.serverPath) }
                            else container.downloads.enqueue(listOf(track))
                        },
                    )
                }
            }
        }

        if (tracks.isEmpty()) {
            item {
                Column(Modifier.fillMaxWidth().padding(32.dp), horizontalAlignment = Alignment.CenterHorizontally) {
                    Text("Nothing matches that.", style = MaterialTheme.typography.bodyMedium)
                    TextButton(onClick = { query = "" }) { Text("Clear search") }
                }
            }
        }
    }
}

fun CloudTrackEntity.artworkModel(baseUrl: String): Any? =
    if (hasArtwork) BridgeUrls.artwork(baseUrl, serverPath) else null

fun Double.asClock(): String {
    if (this <= 0) return "--:--"
    val total = this.toInt()
    return "%d:%02d".format(total / 60, total % 60)
}

package com.lumisound.android.ui.screens.library

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.MoreVert
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
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
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.QueueMusic
import androidx.compose.material3.Icon
import com.lumisound.android.AppContainer
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.lazy.grid.GridCells
import androidx.compose.foundation.lazy.grid.LazyVerticalGrid
import androidx.compose.foundation.lazy.grid.items
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.ui.draw.clip
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.material.icons.filled.PlayArrow
import androidx.compose.material.icons.filled.Refresh
import androidx.compose.material.icons.filled.Shuffle
import com.lumisound.android.data.db.PlaylistEntity
import com.lumisound.android.data.db.PlaylistTrackEntity
import com.lumisound.android.playback.toPlayable
import com.lumisound.android.ui.components.CollageArt
import com.lumisound.android.ui.components.Eyebrow
import com.lumisound.android.ui.components.GlassButton
import com.lumisound.android.ui.components.GlassIconButton
import com.lumisound.android.ui.components.GlowButton
import com.lumisound.android.ui.components.ScreenTitle
import com.lumisound.android.ui.components.trackSubtitle
import com.lumisound.android.ui.components.EmptyState
import com.lumisound.android.ui.components.OneLine
import com.lumisound.android.ui.components.Pill
import com.lumisound.android.ui.components.TrackRow
import kotlinx.coroutines.launch

/**
 * The account's cloud playlists, shared with Lumisound: a rename here shows up there.
 *
 * Every edit goes to the bridge first and the local row is refreshed from what the
 * server returns -- notably including the id the server assigns each track, which is
 * what a later removal needs and what guessing the row locally would not have.
 *
 * Laid out as a wall of collages rather than a list: a playlist is recognised by its cover
 * long before its name is read. Opening one gives it a whole page -- a large collage, its
 * numbers, and Play / Shuffle -- instead of a bare list with a back link.
 */
@Composable
fun PlaylistsScreen(container: AppContainer, onBack: (() -> Unit)? = null, initialPlaylistId: String? = null) {
    val scope = rememberCoroutineScope()
    var openPlaylistId by remember { mutableStateOf(initialPlaylistId) }
    var creating by remember { mutableStateOf(false) }
    var renaming by remember { mutableStateOf<Pair<String, String>?>(null) }
    var busyNote by remember { mutableStateOf<String?>(null) }
    val playback by container.player.state.collectAsStateWithLifecycle()

    val playlists by container.database.playlists().observeAll()
        .collectAsStateWithLifecycle(initialValue = emptyList())

    val selected = openPlaylistId
    if (selected != null) {
        val tracks by container.database.playlists().observeTracks(selected)
            .collectAsStateWithLifecycle(initialValue = emptyList())
        val playlist = playlists.firstOrNull { it.id == selected }

        /** Each entry by whichever handle it has: a cloud path in the mirror, or a page URL. */
        suspend fun playables(): List<com.lumisound.android.playback.PlayableTrack> = tracks.mapNotNull { track ->
            track.localSongId?.let { path ->
                container.database.cloudTracks().byPath(path)?.toPlayable(container.config.baseUrl, container.downloads.isDownloaded(path))
            } ?: track.trackUrl?.let { url ->
                com.lumisound.android.ui.screens.home.streamFromPageUrl(url, track.title, track.artist)
                    ?.toPlayable(container.config.baseUrl)
            }
        }

        PlaylistDetailContent(
            name = playlist?.name ?: "Playlist",
            playlistId = selected,
            tracks = tracks,
            playingTitle = playback.title,
            onBack = { if (initialPlaylistId != null && onBack != null) onBack() else openPlaylistId = null },
            onPlay = { shuffle ->
                scope.launch {
                    val list = playables()
                    if (list.isEmpty()) busyNote = "None of these tracks are playable on this device yet." else {
                        container.player.setShuffle(shuffle)
                        container.player.play(list, if (shuffle) list.indices.random() else 0)
                    }
                }
            },
            onPlayAt = { index ->
                scope.launch {
                    val list = playables()
                    val title = tracks.getOrNull(index)?.title
                    val start = list.indexOfFirst { it.title == title }.coerceAtLeast(0)
                    if (list.isNotEmpty()) container.player.play(list, start)
                }
            },
            onRefresh = { scope.launch { container.libraryRepository.refreshPlaylist(selected) } },
            onRemove = { remoteId ->
                scope.launch {
                    val ok = container.libraryRepository.removeFromPlaylist(selected, remoteId)
                    if (!ok) busyNote = "Could not remove that track."
                }
            },
            note = busyNote,
        )
        return
    }

    PlaylistGridContent(
        playlists = playlists,
        note = busyNote,
        onBack = onBack,
        onOpen = { openPlaylistId = it.id },
        onCreate = { creating = true },
        onRename = { renaming = it.id to it.name },
        onDelete = { playlist ->
            scope.launch {
                if (!container.libraryRepository.deletePlaylist(playlist.id)) busyNote = "Could not delete that playlist."
            }
        },
    )

    if (creating) {
        NameDialog(
            title = "New playlist",
            initial = "",
            onDismiss = { creating = false },
            onConfirm = { name ->
                creating = false
                scope.launch {
                    if (container.libraryRepository.createPlaylist(name, null) == null) {
                        busyNote = "Could not create that playlist."
                    }
                }
            },
        )
    }

    renaming?.let { (id, currentName) ->
        NameDialog(
            title = "Rename playlist",
            initial = currentName,
            onDismiss = { renaming = null },
            onConfirm = { name ->
                renaming = null
                scope.launch {
                    if (!container.libraryRepository.renamePlaylist(id, name)) {
                        busyNote = "Could not rename that playlist."
                    }
                }
            },
        )
    }
}

private fun playlistCollageKeys(id: String, name: String) = listOf(id, name, "$id-2", "$name-3")

@Composable
fun PlaylistGridContent(
    playlists: List<PlaylistEntity>,
    note: String?,
    onBack: (() -> Unit)?,
    onOpen: (PlaylistEntity) -> Unit,
    onCreate: () -> Unit,
    onRename: (PlaylistEntity) -> Unit,
    onDelete: (PlaylistEntity) -> Unit,
) {
    Column(Modifier.fillMaxSize()) {
        ScreenTitle(
            "Playlists",
            subtitle = "${playlists.size} in the cloud · shared with Lumisound",
            eyebrow = "Collections",
            onBack = onBack,
            trailing = { GlassButton("New", Icons.Filled.Add, onClick = onCreate) },
        )
        note?.let {
            Text(it, color = MaterialTheme.colorScheme.error, style = MaterialTheme.typography.labelSmall, modifier = Modifier.padding(horizontal = 20.dp))
        }
        if (playlists.isEmpty()) {
            EmptyState(
                icon = Icons.Filled.QueueMusic,
                title = "No playlists yet",
                message = "Create one here, or import the playlists this account already has in Lumisound.",
                action = { GlowButton("New playlist", Icons.Filled.Add, onClick = onCreate) },
            )
            return@Column
        }
        LazyVerticalGrid(
            columns = GridCells.Fixed(2),
            contentPadding = PaddingValues(start = 16.dp, end = 16.dp, bottom = 24.dp),
            horizontalArrangement = Arrangement.spacedBy(14.dp),
            verticalArrangement = Arrangement.spacedBy(18.dp),
            modifier = Modifier.fillMaxSize(),
        ) {
            items(playlists, key = { it.id }) { playlist ->
                var menuOpen by remember { mutableStateOf(false) }
                Column(Modifier.clip(RoundedCornerShape(22.dp)).clickable { onOpen(playlist) }) {
                    Box {
                        CollageArt(
                            keys = playlistCollageKeys(playlist.id, playlist.name),
                            size = 170.dp,
                            corner = 22.dp,
                            modifier = Modifier.fillMaxWidth().aspectRatio(1f),
                        )
                        Box(Modifier.align(Alignment.TopEnd).padding(6.dp)) {
                            GlassIconButton(Icons.Filled.MoreVert, "More", onClick = { menuOpen = true })
                            DropdownMenu(expanded = menuOpen, onDismissRequest = { menuOpen = false }) {
                                DropdownMenuItem(text = { Text("Rename") }, onClick = { menuOpen = false; onRename(playlist) })
                                DropdownMenuItem(text = { Text("Delete") }, onClick = { menuOpen = false; onDelete(playlist) })
                            }
                        }
                    }
                    Spacer(Modifier.height(8.dp))
                    OneLine(playlist.name, style = MaterialTheme.typography.titleSmall)
                    Text(
                        "${playlist.trackCount} track${if (playlist.trackCount == 1) "" else "s"}",
                        style = MaterialTheme.typography.labelMedium,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                }
            }
        }
    }
}

@Composable
fun PlaylistDetailContent(
    name: String,
    playlistId: String,
    tracks: List<PlaylistTrackEntity>,
    playingTitle: String?,
    onBack: () -> Unit,
    onPlay: (shuffle: Boolean) -> Unit,
    onPlayAt: (Int) -> Unit,
    onRefresh: () -> Unit,
    onRemove: (String) -> Unit,
    note: String?,
) {
    val totalSeconds = tracks.sumOf { (it.durationSeconds ?: 0.0).toInt() }
    LazyColumn(Modifier.fillMaxSize(), contentPadding = PaddingValues(bottom = 24.dp)) {
        item {
            Row(Modifier.fillMaxWidth().padding(start = 14.dp, end = 14.dp, top = 8.dp)) {
                GlassIconButton(Icons.AutoMirrored.Filled.ArrowBack, "Back", onClick = onBack)
                Spacer(Modifier.weight(1f))
                GlassIconButton(Icons.Filled.Refresh, "Refresh", onClick = onRefresh)
            }
        }
        item {
            Column(Modifier.fillMaxWidth().padding(horizontal = 20.dp), horizontalAlignment = Alignment.CenterHorizontally) {
                CollageArt(keys = playlistCollageKeys(playlistId, name), size = 220.dp, corner = 30.dp)
                Spacer(Modifier.height(18.dp))
                Eyebrow("Playlist", color = com.lumisound.android.ui.theme.LocalLumiPalette.current.accent)
                Text(name, style = MaterialTheme.typography.displaySmall, textAlign = TextAlign.Center, maxLines = 2)
                Spacer(Modifier.height(6.dp))
                Text(
                    "${tracks.size} tracks" + if (totalSeconds > 0) " · ${totalSeconds / 60} min" else "",
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
                Spacer(Modifier.height(16.dp))
                Row(horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                    GlowButton("Play", Icons.Filled.PlayArrow, onClick = { onPlay(false) }, modifier = Modifier.weight(1f))
                    GlassButton("Shuffle", Icons.Filled.Shuffle, onClick = { onPlay(true) }, modifier = Modifier.weight(1f))
                }
                note?.let {
                    Spacer(Modifier.height(8.dp))
                    Text(it, color = MaterialTheme.colorScheme.error, style = MaterialTheme.typography.labelSmall)
                }
                Spacer(Modifier.height(12.dp))
            }
        }
        itemsIndexed(tracks, key = { _, it -> it.position }) { index, track ->
            var menuOpen by remember { mutableStateOf(false) }
            Box {
                TrackRow(
                    title = track.title ?: "Untitled",
                    subtitle = trackSubtitle(track.title.orEmpty(), track.artist, track.album),
                    artworkModel = null,
                    fallbackKey = track.localSongId ?: track.trackUrl ?: track.title ?: track.position.toString(),
                    duration = track.durationSeconds?.takeIf { it > 0 }?.let { "%d:%02d".format(it.toInt() / 60, it.toInt() % 60) },
                    isPlaying = playingTitle != null && playingTitle == track.title,
                    onClick = { onPlayAt(index) },
                    // Only a track the server gave an id to can be removed; a row that
                    // somehow lacks one shows no control rather than a button that 404s.
                    onMenu = track.remoteId?.let { { menuOpen = true } },
                )
                DropdownMenu(expanded = menuOpen, onDismissRequest = { menuOpen = false }) {
                    track.remoteId?.let { remoteId ->
                        DropdownMenuItem(text = { Text("Remove from playlist") }, onClick = { menuOpen = false; onRemove(remoteId) })
                    }
                }
            }
        }
    }
}

@Composable
private fun NameDialog(title: String, initial: String, onDismiss: () -> Unit, onConfirm: (String) -> Unit) {
    var text by remember { mutableStateOf(initial) }
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text(title) },
        text = {
            OutlinedTextField(
                value = text,
                onValueChange = { text = it },
                label = { Text("Name") },
                singleLine = true,
            )
        },
        confirmButton = {
            TextButton(enabled = text.isNotBlank(), onClick = { onConfirm(text.trim()) }) { Text("Save") }
        },
        dismissButton = { TextButton(onClick = onDismiss) { Text("Cancel") } },
    )
}

/**
 * "Add to playlist" from anywhere a track is shown. Lists the account's playlists and
 * can create one inline, because the moment someone wants to file a track is exactly
 * when they discover they have nowhere to put it.
 */
@Composable
fun AddToPlaylistDialog(
    container: AppContainer,
    title: String,
    artist: String?,
    album: String?,
    songId: String,
    durationSeconds: Int,
    onDismiss: () -> Unit,
) {
    val scope = rememberCoroutineScope()
    val playlists by container.database.playlists().observeAll()
        .collectAsStateWithLifecycle(initialValue = emptyList())
    var creating by remember { mutableStateOf(false) }
    var note by remember { mutableStateOf<String?>(null) }

    fun add(playlistId: String) {
        scope.launch {
            val ok = container.libraryRepository.addToPlaylist(
                playlistId = playlistId,
                title = title,
                artist = artist,
                album = album,
                localSongId = songId,
                durationSeconds = durationSeconds,
            )
            note = if (ok) "Added." else "The server refused that — you may only have viewer access."
            if (ok) onDismiss()
        }
    }

    if (creating) {
        NameDialog(
            title = "New playlist",
            initial = "",
            onDismiss = { creating = false },
            onConfirm = { name ->
                creating = false
                scope.launch {
                    val id = container.libraryRepository.createPlaylist(name, null)
                    if (id == null) note = "Could not create that playlist." else add(id)
                }
            },
        )
        return
    }

    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text("Add to playlist") },
        text = {
            Column {
                Text(title, style = MaterialTheme.typography.bodyMedium, maxLines = 1, overflow = TextOverflow.Ellipsis)
                note?.let { Text(it, style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.error) }
                LazyColumn(Modifier.fillMaxWidth()) {
                    items(playlists, key = { it.id }) { playlist ->
                        Text(
                            playlist.name,
                            style = MaterialTheme.typography.bodyMedium,
                            modifier = Modifier
                                .fillMaxWidth()
                                .clickable { add(playlist.id) }
                                .padding(vertical = 12.dp),
                        )
                    }
                }
            }
        },
        confirmButton = { TextButton(onClick = { creating = true }) { Text("New playlist") } },
        dismissButton = { TextButton(onClick = onDismiss) { Text("Cancel") } },
    )
}

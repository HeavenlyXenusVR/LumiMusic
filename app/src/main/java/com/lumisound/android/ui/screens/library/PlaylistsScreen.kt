package com.lumisound.android.ui.screens.library

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
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
import com.lumisound.android.AppContainer
import kotlinx.coroutines.launch

/**
 * The account's cloud playlists, shared with Lumisound: a rename here shows up there.
 *
 * Every edit goes to the bridge first and the local row is refreshed from what the
 * server returns -- notably including the id the server assigns each track, which is
 * what a later removal needs and what guessing the row locally would not have.
 */
@Composable
fun PlaylistsScreen(container: AppContainer) {
    val scope = rememberCoroutineScope()
    var openPlaylistId by remember { mutableStateOf<String?>(null) }
    var creating by remember { mutableStateOf(false) }
    var renaming by remember { mutableStateOf<Pair<String, String>?>(null) }
    var busyNote by remember { mutableStateOf<String?>(null) }

    val playlists by container.database.playlists().observeAll()
        .collectAsStateWithLifecycle(initialValue = emptyList())

    val selected = openPlaylistId
    if (selected != null) {
        val tracks by container.database.playlists().observeTracks(selected)
            .collectAsStateWithLifecycle(initialValue = emptyList())
        val playlist = playlists.firstOrNull { it.id == selected }
        Column(Modifier.fillMaxSize()) {
            Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
                TextButton(onClick = { openPlaylistId = null }) { Text("‹ All playlists") }
                Text(
                    playlist?.name ?: "Playlist",
                    style = MaterialTheme.typography.titleMedium,
                    modifier = Modifier.weight(1f),
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                )
                TextButton(onClick = { scope.launch { container.libraryRepository.refreshPlaylist(selected) } }) {
                    Text("Refresh")
                }
            }
            LazyColumn(Modifier.fillMaxSize()) {
                items(tracks, key = { it.position }) { track ->
                    Row(
                        Modifier.fillMaxWidth().padding(start = 16.dp, top = 8.dp, bottom = 8.dp),
                        verticalAlignment = Alignment.CenterVertically,
                    ) {
                        Column(Modifier.weight(1f)) {
                            Text(
                                track.title ?: "Untitled",
                                maxLines = 1,
                                overflow = TextOverflow.Ellipsis,
                                style = MaterialTheme.typography.bodyMedium,
                            )
                            Text(
                                track.artist.orEmpty(),
                                maxLines = 1,
                                style = MaterialTheme.typography.labelSmall,
                                color = MaterialTheme.colorScheme.onSurfaceVariant,
                            )
                        }
                        // Only a track the server gave an id to can be removed; a row that
                        // somehow lacks one shows no control rather than a button that 404s.
                        track.remoteId?.let { remoteId ->
                            TextButton(onClick = {
                                scope.launch {
                                    val ok = container.libraryRepository.removeFromPlaylist(selected, remoteId)
                                    if (!ok) busyNote = "Could not remove that track."
                                }
                            }) { Text("Remove") }
                        }
                    }
                }
            }
        }
        return
    }

    Column(Modifier.fillMaxSize()) {
        Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
            Text(
                "Cloud playlists",
                style = MaterialTheme.typography.titleMedium,
                modifier = Modifier.weight(1f).padding(start = 16.dp),
            )
            TextButton(onClick = { creating = true }) { Text("New") }
        }
        busyNote?.let {
            Text(
                it,
                color = MaterialTheme.colorScheme.error,
                style = MaterialTheme.typography.labelSmall,
                modifier = Modifier.padding(horizontal = 16.dp),
            )
        }

        if (playlists.isEmpty()) {
            Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                Text(
                    "No playlists yet.",
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }
        } else {
            LazyColumn(Modifier.fillMaxSize()) {
                items(playlists, key = { it.id }) { playlist ->
                    var menuOpen by remember { mutableStateOf(false) }
                    Row(
                        Modifier
                            .fillMaxWidth()
                            .clickable { openPlaylistId = playlist.id }
                            .padding(start = 16.dp, top = 8.dp, bottom = 8.dp),
                        verticalAlignment = Alignment.CenterVertically,
                    ) {
                        Column(Modifier.weight(1f)) {
                            Text(
                                playlist.name,
                                maxLines = 1,
                                overflow = TextOverflow.Ellipsis,
                                style = MaterialTheme.typography.bodyMedium,
                            )
                            Text(
                                "${playlist.trackCount} track${if (playlist.trackCount == 1) "" else "s"}",
                                style = MaterialTheme.typography.labelSmall,
                                color = MaterialTheme.colorScheme.onSurfaceVariant,
                            )
                        }
                        Box {
                            IconButton(onClick = { menuOpen = true }) { Icon(Icons.Filled.MoreVert, "More") }
                            DropdownMenu(expanded = menuOpen, onDismissRequest = { menuOpen = false }) {
                                DropdownMenuItem(
                                    text = { Text("Rename") },
                                    onClick = { menuOpen = false; renaming = playlist.id to playlist.name },
                                )
                                DropdownMenuItem(
                                    text = { Text("Delete") },
                                    onClick = {
                                        menuOpen = false
                                        scope.launch {
                                            if (!container.libraryRepository.deletePlaylist(playlist.id)) {
                                                busyNote = "Could not delete that playlist."
                                            }
                                        }
                                    },
                                )
                            }
                        }
                    }
                }
            }
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

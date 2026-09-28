package com.lumisound.android.ui.screens.library

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.lumisound.android.AppContainer

/**
 * Cloud playlists (`GET /user/playlists`) and their tracks. Read-only in this
 * milestone: every row is the account's own server-side playlist, shared with
 * Lumisound, and editing them belongs with the playlist-CRUD chunk rather than
 * being half-built here.
 */
@Composable
fun PlaylistsScreen(container: AppContainer) {
    var openPlaylistId by remember { mutableStateOf<String?>(null) }
    val playlists by container.database.playlists().observeAll()
        .collectAsStateWithLifecycle(initialValue = emptyList())

    val selected = openPlaylistId
    if (selected != null) {
        val tracks by container.database.playlists().observeTracks(selected)
            .collectAsStateWithLifecycle(initialValue = emptyList())
        val playlist = playlists.firstOrNull { it.id == selected }
        Column(Modifier.fillMaxSize()) {
            TextButton(onClick = { openPlaylistId = null }) { Text("‹ All playlists") }
            Text(
                playlist?.name ?: "Playlist",
                style = MaterialTheme.typography.titleMedium,
                modifier = Modifier.padding(horizontal = 16.dp, vertical = 4.dp),
            )
            LazyColumn(Modifier.fillMaxSize()) {
                items(tracks, key = { it.position }) { track ->
                    Column(Modifier.fillMaxWidth().padding(horizontal = 16.dp, vertical = 10.dp)) {
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
                }
            }
        }
        return
    }

    if (playlists.isEmpty()) {
        Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
            Text(
                "No playlists imported yet.",
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
        }
        return
    }

    LazyColumn(Modifier.fillMaxSize()) {
        items(playlists, key = { it.id }) { playlist ->
            Column(
                Modifier
                    .fillMaxWidth()
                    .clickable { openPlaylistId = playlist.id }
                    .padding(horizontal = 16.dp, vertical = 12.dp)
            ) {
                Text(playlist.name, maxLines = 1, overflow = TextOverflow.Ellipsis, style = MaterialTheme.typography.bodyMedium)
                Text(
                    "${playlist.trackCount} track${if (playlist.trackCount == 1) "" else "s"}",
                    style = MaterialTheme.typography.labelSmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }
        }
    }
}

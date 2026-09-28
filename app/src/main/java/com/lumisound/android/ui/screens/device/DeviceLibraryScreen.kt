package com.lumisound.android.ui.screens.device

import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.clickable
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.rememberScrollState
import androidx.compose.material3.Button
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.FilterChip
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
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.lumisound.android.AppContainer
import com.lumisound.android.data.db.LocalTrackEntity
import com.lumisound.android.library.AudioPermission
import com.lumisound.android.playback.toPlayable
import kotlinx.coroutines.launch

private enum class Grouping(val label: String) { Songs("Songs"), Artists("Artists"), Albums("Albums"), Folders("Folders") }

/**
 * The music on the device itself. Separate tab from the cloud library rather than a
 * merged "everything" list: the two have different identity and different failure
 * modes, and a user looking for a file they just copied over should not have to guess
 * whether a missing track is a scan problem or a sync problem.
 */
@Composable
fun DeviceLibraryScreen(container: AppContainer) {
    val scope = rememberCoroutineScope()
    val scanState by container.libraryScanner.state.collectAsStateWithLifecycle()
    var grouping by remember { mutableStateOf(Grouping.Songs) }
    var query by remember { mutableStateOf("") }
    var openGroup by remember { mutableStateOf<String?>(null) }

    val permission = rememberLauncherForActivityResult(ActivityResultContracts.RequestPermission()) { granted ->
        if (granted) scope.launch { container.libraryScanner.scan() }
    }

    val tracks by remember(query, grouping, openGroup) {
        val dao = container.database.localTracks()
        when {
            query.isNotBlank() -> dao.search(query)
            openGroup != null && grouping == Grouping.Artists -> dao.observeByArtist(openGroup!!)
            openGroup != null && grouping == Grouping.Albums -> dao.observeByAlbum(openGroup!!)
            openGroup != null && grouping == Grouping.Folders -> dao.observeByFolder(openGroup!!)
            else -> dao.observeAll()
        }
    }.collectAsStateWithLifecycle(initialValue = emptyList())

    val groups by remember(grouping) {
        val dao = container.database.localTracks()
        when (grouping) {
            Grouping.Artists -> dao.observeArtists()
            Grouping.Albums -> dao.observeAlbums()
            Grouping.Folders -> dao.observeFolders()
            Grouping.Songs -> kotlinx.coroutines.flow.flowOf(emptyList())
        }
    }.collectAsStateWithLifecycle(initialValue = emptyList())

    fun play(index: Int) {
        container.player.play(tracks.map(LocalTrackEntity::toPlayable), index)
    }

    Column(Modifier.fillMaxSize()) {
        Row(
            Modifier.fillMaxWidth().padding(horizontal = 12.dp, vertical = 6.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.SpaceBetween,
        ) {
            Text(
                buildString {
                    append("${tracks.size} shown")
                    scanState.lastDurationMs?.let { append(" · scanned in ${it}ms") }
                },
                style = MaterialTheme.typography.labelSmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
            TextButton(
                enabled = !scanState.running,
                // The permission launcher runs the scan itself when granted, so a denied
                // prompt never leaves a scan half-started.
                onClick = { permission.launch(AudioPermission.required) },
            ) {
                Text(if (scanState.running) "Scanning…" else "Rescan")
            }
        }

        Row(
            Modifier.fillMaxWidth().horizontalScroll(rememberScrollState()).padding(horizontal = 12.dp),
            horizontalArrangement = Arrangement.spacedBy(8.dp),
        ) {
            Grouping.entries.forEach { entry ->
                FilterChip(
                    selected = grouping == entry,
                    onClick = { grouping = entry; openGroup = null },
                    label = { Text(entry.label) },
                )
            }
        }

        OutlinedTextField(
            value = query,
            onValueChange = { query = it },
            label = { Text("Search this device") },
            singleLine = true,
            modifier = Modifier.fillMaxWidth().padding(horizontal = 16.dp, vertical = 8.dp),
        )

        if (scanState.running && tracks.isEmpty()) {
            Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) { CircularProgressIndicator() }
            return@Column
        }

        scanState.error?.let { error ->
            Text(
                error,
                color = MaterialTheme.colorScheme.error,
                style = MaterialTheme.typography.bodySmall,
                modifier = Modifier.padding(horizontal = 16.dp, vertical = 4.dp),
            )
        }

        if (tracks.isEmpty() && groups.isEmpty()) {
            Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                Column(horizontalAlignment = Alignment.CenterHorizontally) {
                    Text("No music found on this device yet.", style = MaterialTheme.typography.bodyMedium)
                    Spacer(Modifier.height(6.dp))
                    Text(
                        "LumiMusic needs permission to read audio files before it can scan.",
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                        textAlign = TextAlign.Center,
                    )
                    Spacer(Modifier.height(14.dp))
                    Button(onClick = { permission.launch(AudioPermission.required) }) {
                        Text("Scan for music")
                    }
                }
            }
            return@Column
        }

        val showingGroupList = grouping != Grouping.Songs && openGroup == null && query.isBlank()
        if (showingGroupList) {
            LazyColumn(Modifier.fillMaxSize()) {
                items(groups, key = { it }) { group ->
                    Text(
                        group.ifBlank { "Unknown" },
                        style = MaterialTheme.typography.bodyMedium,
                        modifier = Modifier
                            .fillMaxWidth()
                            .clickable { openGroup = group }
                            .padding(horizontal = 16.dp, vertical = 13.dp),
                    )
                }
            }
            return@Column
        }

        openGroup?.let { group ->
            TextButton(onClick = { openGroup = null }) { Text("‹ ${grouping.label}") }
            Text(
                group,
                style = MaterialTheme.typography.titleMedium,
                modifier = Modifier.padding(horizontal = 16.dp),
            )
        }

        LazyColumn(Modifier.fillMaxSize()) {
            items(tracks, key = { it.mediaStoreId }) { track ->
                Column(
                    Modifier
                        .fillMaxWidth()
                        .clickable { play(tracks.indexOfFirst { it.mediaStoreId == track.mediaStoreId }) }
                        .padding(horizontal = 16.dp, vertical = 10.dp)
                ) {
                    Text(track.title, maxLines = 1, overflow = TextOverflow.Ellipsis, style = MaterialTheme.typography.bodyMedium)
                    Text(
                        listOfNotNull(
                            track.artist.takeIf { it.isNotBlank() },
                            track.album.takeIf { it.isNotBlank() },
                            (track.durationMs / 1000).takeIf { it > 0 }?.let { "%d:%02d".format(it / 60, it % 60) },
                        ).joinToString(" · "),
                        maxLines = 1,
                        style = MaterialTheme.typography.labelSmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                }
            }
        }
    }
}

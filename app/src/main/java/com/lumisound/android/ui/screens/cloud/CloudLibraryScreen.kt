package com.lumisound.android.ui.screens.cloud

import androidx.compose.foundation.clickable
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
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Lock
import androidx.compose.material.icons.filled.MusicNote
import androidx.compose.material3.Button
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import coil3.compose.AsyncImage
import com.lumisound.android.AppContainer
import com.lumisound.android.bridge.BridgeUrls
import com.lumisound.android.data.db.CloudTrackEntity
import kotlinx.coroutines.flow.flowOf

/**
 * The account's cloud library: every track in its personal server storage,
 * including Lumisound-locked `.lms` files, which stream and unmask in flight.
 */
@Composable
fun CloudLibraryScreen(container: AppContainer, onOpenImport: () -> Unit) {
    var query by remember { mutableStateOf("") }
    val tracks by remember(query) {
        if (query.isBlank()) container.database.cloudTracks().observeAll()
        else container.database.cloudTracks().search(query)
    }.collectAsStateWithLifecycle(initialValue = emptyList())

    Column(Modifier.fillMaxSize()) {
        OutlinedTextField(
            value = query,
            onValueChange = { query = it },
            label = { Text("Search your cloud library") },
            singleLine = true,
            modifier = Modifier.fillMaxWidth().padding(horizontal = 16.dp, vertical = 8.dp),
        )

        if (tracks.isEmpty()) {
            EmptyLibrary(query.isNotBlank(), onOpenImport)
            return@Column
        }

        LazyColumn(Modifier.fillMaxSize()) {
            items(tracks, key = { it.serverPath }) { track ->
                TrackRow(
                    track = track,
                    baseUrl = container.config.baseUrl,
                    onPlay = {
                        container.player.play(tracks, tracks.indexOfFirst { it.serverPath == track.serverPath })
                    },
                )
            }
        }
    }
}

@Composable
private fun EmptyLibrary(searching: Boolean, onOpenImport: () -> Unit) {
    Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
        Column(horizontalAlignment = Alignment.CenterHorizontally) {
            Text(
                if (searching) "Nothing here matches that." else "No cloud tracks on this device yet.",
                style = MaterialTheme.typography.bodyMedium,
            )
            if (!searching) {
                Spacer(Modifier.height(6.dp))
                Text(
                    "Import this account's cloud data to pull its library down.",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    textAlign = TextAlign.Center,
                )
                Spacer(Modifier.height(14.dp))
                Button(onClick = onOpenImport) { Text("Import cloud data") }
            }
        }
    }
}

@Composable
private fun TrackRow(track: CloudTrackEntity, baseUrl: String, onPlay: () -> Unit) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .clickable(onClick = onPlay)
            .padding(horizontal = 16.dp, vertical = 10.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(12.dp),
    ) {
        val artwork = if (track.hasArtwork) BridgeUrls.artwork(baseUrl, track.serverPath) else null
        if (artwork != null) {
            AsyncImage(
                model = artwork,
                contentDescription = null,
                contentScale = ContentScale.Crop,
                modifier = Modifier.size(46.dp).clip(RoundedCornerShape(6.dp)),
            )
        } else {
            Box(
                Modifier
                    .size(46.dp)
                    .clip(RoundedCornerShape(6.dp)),
                contentAlignment = Alignment.Center,
            ) {
                Icon(
                    Icons.Filled.MusicNote,
                    contentDescription = null,
                    tint = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }
        }

        Column(Modifier.weight(1f)) {
            Text(track.title, maxLines = 1, overflow = TextOverflow.Ellipsis, style = MaterialTheme.typography.bodyMedium)
            Text(
                listOfNotNull(track.artist.takeIf { it.isNotBlank() }, track.album.takeIf { it.isNotBlank() })
                    .joinToString(" · "),
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
                style = MaterialTheme.typography.labelSmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
        }

        if (track.isLocked) {
            // Worth showing: these are the tracks that no other Android player can
            // open at all, and their bytes are unmasked on the fly here.
            Icon(
                Icons.Filled.Lock,
                contentDescription = "Lumisound-locked track",
                tint = MaterialTheme.colorScheme.onSurfaceVariant,
                modifier = Modifier.size(16.dp),
            )
        }
        Text(track.durationSeconds.asClock(), style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
    }
}

fun Double.asClock(): String {
    if (this <= 0) return "--:--"
    val total = this.toInt()
    return "%d:%02d".format(total / 60, total % 60)
}

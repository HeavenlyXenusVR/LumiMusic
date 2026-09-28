package com.lumisound.android.ui.screens.downloads

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
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.lumisound.android.AppContainer
import kotlinx.coroutines.launch

/**
 * Tracks saved for offline playback. A locked track's file stays masked on disk
 * exactly as the server sent it -- the player unmasks it on the way to the decoder,
 * so an offline copy is no more playable outside this app than a streamed one.
 */
@Composable
fun DownloadsScreen(container: AppContainer) {
    val scope = rememberCoroutineScope()
    val downloads by container.database.downloads().observeAll()
        .collectAsStateWithLifecycle(initialValue = emptyList())
    val active by container.downloads.active.collectAsStateWithLifecycle()
    val queued by container.downloads.queued.collectAsStateWithLifecycle()

    Column(Modifier.fillMaxSize()) {
        active?.let { progress ->
            Column(Modifier.fillMaxWidth().padding(16.dp)) {
                Text(
                    "Downloading ${progress.title}",
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                    style = MaterialTheme.typography.bodyMedium,
                )
                Spacer(Modifier.height(6.dp))
                if (progress.totalBytes > 0) {
                    LinearProgressIndicator(progress = { progress.fraction }, modifier = Modifier.fillMaxWidth())
                } else {
                    // The server does not always send a length; a spinner is honest about
                    // not knowing how far along this is.
                    LinearProgressIndicator(Modifier.fillMaxWidth())
                }
                Spacer(Modifier.height(4.dp))
                Text(
                    "${progress.bytesRead / 1_048_576} MB" +
                        if (progress.totalBytes > 0) " of ${progress.totalBytes / 1_048_576} MB" else "",
                    style = MaterialTheme.typography.labelSmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }
        }

        if (queued.isNotEmpty()) {
            Row(
                Modifier.fillMaxWidth().padding(horizontal = 16.dp),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.SpaceBetween,
            ) {
                Text(
                    "${queued.size} waiting",
                    style = MaterialTheme.typography.labelSmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
                TextButton(onClick = container.downloads::cancelAll) { Text("Cancel queue") }
            }
        }

        if (downloads.isEmpty()) {
            Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                Text(
                    "Nothing saved offline yet.",
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }
            return@Column
        }

        LazyColumn(Modifier.fillMaxSize()) {
            items(downloads, key = { it.serverPath }) { download ->
                Row(
                    Modifier.fillMaxWidth().padding(start = 16.dp, top = 8.dp, bottom = 8.dp),
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    Column(Modifier.weight(1f)) {
                        Text(
                            download.serverPath.substringAfterLast('/'),
                            maxLines = 1,
                            overflow = TextOverflow.Ellipsis,
                            style = MaterialTheme.typography.bodyMedium,
                        )
                        Text(
                            "${download.sizeBytes / 1_048_576} MB" + if (download.isLocked) " · locked" else "",
                            style = MaterialTheme.typography.labelSmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                        )
                    }
                    TextButton(onClick = { scope.launch { container.downloads.remove(download.serverPath) } }) {
                        Text("Remove")
                    }
                }
            }
        }
    }
}

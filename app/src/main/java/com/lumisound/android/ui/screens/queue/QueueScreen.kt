package com.lumisound.android.ui.screens.queue

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.ArrowDownward
import androidx.compose.material.icons.filled.ArrowUpward
import androidx.compose.material.icons.filled.Close
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.lumisound.android.AppContainer
import com.lumisound.android.playback.QueueItem
import kotlinx.coroutines.delay

/**
 * The live queue as the media session holds it -- the session is the single source of
 * truth for what plays next, so this reads it back rather than keeping a second copy
 * the UI would have to hold in step.
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun QueueSheet(container: AppContainer, onDismiss: () -> Unit) {
    val playback by container.player.state.collectAsStateWithLifecycle()
    var items by remember { mutableStateOf<List<QueueItem>>(emptyList()) }

    // Re-read on every playback change and on a short tick: a reorder or removal
    // changes the session without necessarily producing a player event.
    LaunchedEffect(playback.queueIndex, playback.queueSize) {
        items = container.player.queueSnapshot()
    }
    LaunchedEffect(Unit) {
        while (true) {
            delay(1_000)
            items = container.player.queueSnapshot()
        }
    }

    ModalBottomSheet(onDismissRequest = onDismiss) {
        Text(
            "Queue · ${items.size} track${if (items.size == 1) "" else "s"}",
            style = MaterialTheme.typography.titleMedium,
            modifier = Modifier.padding(horizontal = 20.dp, vertical = 8.dp),
        )
        LazyColumn(Modifier.fillMaxWidth().heightIn(max = 480.dp)) {
            items(items, key = { it.index }) { item ->
                Row(
                    Modifier
                        .fillMaxWidth()
                        .clickable { container.player.skipTo(item.index) }
                        .padding(start = 20.dp, top = 6.dp, bottom = 6.dp),
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(4.dp),
                ) {
                    Column(Modifier.weight(1f)) {
                        Text(
                            item.title,
                            maxLines = 1,
                            overflow = TextOverflow.Ellipsis,
                            style = MaterialTheme.typography.bodyMedium,
                            fontWeight = if (item.isCurrent) FontWeight.Bold else FontWeight.Normal,
                            color = if (item.isCurrent) MaterialTheme.colorScheme.primary
                            else MaterialTheme.colorScheme.onSurface,
                        )
                        Text(
                            item.artist,
                            maxLines = 1,
                            style = MaterialTheme.typography.labelSmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                        )
                    }
                    IconButton(
                        enabled = item.index > 0,
                        onClick = {
                            container.player.moveInQueue(item.index, item.index - 1)
                            items = container.player.queueSnapshot()
                        },
                    ) { Icon(Icons.Filled.ArrowUpward, "Move up") }
                    IconButton(
                        enabled = item.index < items.lastIndex,
                        onClick = {
                            container.player.moveInQueue(item.index, item.index + 1)
                            items = container.player.queueSnapshot()
                        },
                    ) { Icon(Icons.Filled.ArrowDownward, "Move down") }
                    IconButton(onClick = {
                        container.player.removeFromQueue(item.index)
                        items = container.player.queueSnapshot()
                    }) { Icon(Icons.Filled.Close, "Remove") }
                }
            }
        }
    }
}

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
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.lumisound.android.AppContainer
import com.lumisound.android.playback.toPlayable
import kotlinx.coroutines.launch

/**
 * The account's favorites, exactly as `GET /user/favorites` reports them --
 * the same rows Lumisound shows, since both clients read and write that one table.
 *
 * A favorite is stored as a `song_id` plus display metadata, NOT as a cloud-track
 * reference, so a favorite only becomes playable here when its id happens to match
 * a cloud track this device has. That is a property of the shared schema, not a
 * gap introduced by this port: iOS favorites its own local library ids too.
 */
@Composable
fun FavoritesScreen(container: AppContainer) {
    val scope = rememberCoroutineScope()
    val favorites by container.database.favorites().observeAll()
        .collectAsStateWithLifecycle(initialValue = emptyList())

    if (favorites.isEmpty()) {
        Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
            Text(
                "No favorites imported yet.",
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
        }
        return
    }

    LazyColumn(Modifier.fillMaxSize()) {
        items(favorites, key = { it.songId }) { favorite ->
            Column(
                Modifier
                    .fillMaxWidth()
                    .clickable {
                        scope.launch {
                            val track = container.database.cloudTracks().byPath(favorite.songId)
                                ?: return@launch
                            container.player.play(
                                listOf(
                                    track.toPlayable(
                                        container.config.baseUrl,
                                        container.downloads.isDownloaded(track.serverPath),
                                    )
                                ),
                                0,
                            )
                        }
                    }
                    .padding(horizontal = 16.dp, vertical = 11.dp)
            ) {
                Text(
                    favorite.title ?: favorite.songId,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                    style = MaterialTheme.typography.bodyMedium,
                )
                Text(
                    favorite.artist.orEmpty(),
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                    style = MaterialTheme.typography.labelSmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }
        }
    }
}

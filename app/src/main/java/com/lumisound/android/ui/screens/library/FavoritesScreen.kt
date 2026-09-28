package com.lumisound.android.ui.screens.library

import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.FavoriteBorder
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.lumisound.android.AppContainer
import com.lumisound.android.playback.toPlayable
import com.lumisound.android.ui.components.EmptyState
import androidx.compose.material.icons.filled.Favorite
import com.lumisound.android.ui.components.IconSectionHeader
import com.lumisound.android.ui.components.ScreenTitle
import com.lumisound.android.ui.theme.SectionTint
import com.lumisound.android.ui.components.TrackRow
import com.lumisound.android.ui.components.trackSubtitle
import kotlinx.coroutines.launch

/**
 * The account's favorites, exactly as the bridge reports them -- the same rows Lumisound
 * shows, since both clients read and write that one table.
 *
 * A favorite is stored as a `song_id` plus display metadata, not as a cloud-track
 * reference, so one is playable here only when its id matches a cloud track this device
 * has. That is a property of the shared schema, not something this port introduced: iOS
 * favorites its own local library ids too.
 */
@Composable
fun FavoritesScreen(container: AppContainer) {
    val scope = rememberCoroutineScope()
    val favorites by container.database.favorites().observeAll()
        .collectAsStateWithLifecycle(initialValue = emptyList())
    val playback by container.player.state.collectAsStateWithLifecycle()

    if (favorites.isEmpty()) {
        EmptyState(
            icon = Icons.Filled.FavoriteBorder,
            title = "No favorites yet",
            message = "Favorite a track here or in Lumisound — the list is shared between both apps.",
        )
        return
    }

    LazyColumn(Modifier.fillMaxSize(), contentPadding = PaddingValues(bottom = 24.dp)) {
        item {
            ScreenTitle("Favorites", subtitle = "${favorites.size} saved · shared with Lumisound")
        }
        item {
            IconSectionHeader(
                Icons.Filled.Favorite,
                "Saved tracks",
                tint = SectionTint.Favorites,
            )
        }
        items(favorites, key = { it.songId }) { favorite ->
            TrackRow(
                title = favorite.title ?: favorite.songId.substringAfterLast('/'),
                subtitle = trackSubtitle(
                    favorite.title ?: favorite.songId,
                    favorite.artist,
                    favorite.album,
                ),
                artworkModel = null,
                fallbackKey = favorite.songId,
                isPlaying = playback.serverPath == favorite.songId,
                onClick = {
                    scope.launch {
                        val track = container.database.cloudTracks().byPath(favorite.songId) ?: return@launch
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
                },
            )
        }
    }
}

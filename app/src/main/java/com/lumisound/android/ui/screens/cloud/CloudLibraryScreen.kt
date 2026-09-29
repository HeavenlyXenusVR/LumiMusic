package com.lumisound.android.ui.screens.cloud

import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.lumisound.android.AppContainer
import com.lumisound.android.bridge.BridgeUrls
import com.lumisound.android.data.db.CloudTrackEntity
import com.lumisound.android.playback.toPlayable
import kotlinx.coroutines.launch

/**
 * The container-bound half of the cloud library: collects state, hands it to
 * [CloudLibraryContent], and turns the content's callbacks into real work.
 */
@Composable
fun CloudLibraryScreen(
    container: AppContainer,
    onOpenImport: () -> Unit,
    onAddToPlaylist: (title: String, artist: String?, album: String?, songId: String, durationSeconds: Int) -> Unit,
    initialView: CloudView = CloudView.All,
    onBack: (() -> Unit)? = null,
) {
    val scope = rememberCoroutineScope()
    var query by remember { mutableStateOf("") }
    var view by remember { mutableStateOf(initialView) }

    val all by remember(query) {
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

    val favoriteSet = favoriteIds.toSet()
    val downloadedSet = downloadedPaths.toSet()
    val shown = when (view) {
        CloudView.All -> all
        CloudView.Recent -> recent
        CloudView.Offline -> all.filter { it.serverPath in downloadedSet }
        CloudView.Favorites -> all.filter { it.serverPath in favoriteSet }
    }

    /** Substitutes an offline copy wherever one exists, so a downloaded track never re-streams. */
    suspend fun playables(list: List<CloudTrackEntity>) = list.map { track ->
        track.toPlayable(container.config.baseUrl, container.downloads.isDownloaded(track.serverPath))
    }

    CloudLibraryContent(
        onBack = onBack,
        tracks = shown,
        recentlyAdded = recent,
        favoriteIds = favoriteSet,
        downloadedPaths = downloadedSet,
        playingPath = playback.serverPath,
        query = query,
        onQueryChange = { query = it },
        view = view,
        onViewChange = { view = it },
        artworkModelFor = { track ->
            if (track.hasArtwork) BridgeUrls.artwork(container.config.baseUrl, track.serverPath) else null
        },
        onPlay = { list, index ->
            scope.launch {
                container.player.setShuffle(false)
                container.player.play(playables(list), index)
            }
        },
        onShuffle = { list ->
            scope.launch {
                container.player.setShuffle(true)
                container.player.play(playables(list), list.indices.randomOrNull() ?: 0)
            }
        },
        onImport = onOpenImport,
        onRefresh = { scope.launch { container.cloudImport.import(setOf(com.lumisound.android.cloud.ImportStage.CloudTracks)) } },
        actions = TrackActions(
            onPlayNext = { track -> scope.launch { container.player.playNext(playables(listOf(track)).first()) } },
            onEnqueue = { track -> scope.launch { container.player.enqueue(playables(listOf(track))) } },
            onToggleFavorite = { track ->
                scope.launch {
                    container.libraryRepository.toggleFavorite(track.serverPath, track.title, track.artist, track.album)
                }
            },
            onAddToPlaylist = { track ->
                onAddToPlaylist(track.title, track.artist, track.album, track.serverPath, track.durationSeconds.toInt())
            },
            onDownload = { track -> container.downloads.enqueue(listOf(track)) },
            onRemoveDownload = { track -> scope.launch { container.downloads.remove(track.serverPath) } },
        ),
    )
}

fun Double.asClock(): String {
    if (this <= 0) return "--:--"
    val total = this.toInt()
    return "%d:%02d".format(total / 60, total % 60)
}

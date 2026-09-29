package com.lumisound.android.ui.screens.home

import android.net.Uri
import android.widget.Toast
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.ui.platform.LocalContext
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.lumisound.android.AppContainer
import com.lumisound.android.bridge.AccountState
import com.lumisound.android.bridge.BridgeUrls
import com.lumisound.android.bridge.model.EpisodeProgressDto
import com.lumisound.android.bridge.model.HistoryEntryDto
import com.lumisound.android.bridge.model.StreamTrackDto
import com.lumisound.android.diagnostics.AppLogger
import com.lumisound.android.playback.toPlayable
import com.lumisound.android.ui.Route
import com.lumisound.android.ui.components.rememberLoadable
import com.lumisound.android.ui.playStreams
import com.lumisound.android.ui.searchAndPlay
import com.lumisound.android.ui.weeklyMixPlayables
import kotlinx.coroutines.launch

/**
 * The container-bound half of Home: one independent fetch per section, so a slow AI pick
 * never holds up the weekly mix, and the callbacks that turn a tap into a queue.
 */
@Composable
fun HomeScreen(container: AppContainer, onOpen: (Route) -> Unit, onOpenFriends: () -> Unit) {
    val scope = rememberCoroutineScope()
    val context = LocalContext.current
    val account by container.account.state.collectAsStateWithLifecycle()
    val user = (account as? AccountState.SignedIn)?.user
    val api = container.http.discovery

    val dailyPick = rememberLoadable(Unit, "home.dailyPick") { api.dailyPick() }
    val weeklyMix = rememberLoadable(Unit, "home.weeklyMix") { api.weeklyMix() }
    val discoverMix = rememberLoadable(Unit, "home.discoverMix") { api.discoverMix() }
    val continueListening = rememberLoadable(Unit, "home.continue") { container.http.podcasts.progress() }
    val onThisDay = rememberLoadable(Unit, "home.onThisDay") { api.onThisDay() }
    val recent = rememberLoadable(Unit, "home.recent") {
        // Distinct by what was played: a track on repeat is one card, not a row of copies.
        container.http.libraryData.history(limit = 60)
            .distinctBy { it.localSongId ?: it.trackUrl ?: "${it.title}|${it.artist}" }
            .take(20)
    }
    val trending = rememberLoadable(Unit, "home.trending") { api.communityTrending().tracks }
    val twin = rememberLoadable(Unit, "home.twin") { api.listeningTwin() }
    val unread = rememberLoadable(Unit, "home.unread") { api.notifications(limit = 99, unreadOnly = true).size }

    fun toast(message: String) = Toast.makeText(context, message, Toast.LENGTH_SHORT).show()

    HomeContent(
        data = HomeData(
            displayName = user?.displayName?.takeIf { it.isNotBlank() } ?: user?.username,
            unreadNotifications = unread.state.valueOrNull ?: 0,
            dailyPick = dailyPick.state,
            weeklyMix = weeklyMix.state,
            discoverMix = discoverMix.state,
            continueListening = continueListening.state,
            onThisDay = onThisDay.state,
            recentlyPlayed = recent.state,
            trending = trending.state,
            twin = twin.state,
        ),
        callbacks = HomeCallbacks(
            onShortcut = { shortcut ->
                when (shortcut) {
                    HomeShortcut.Stats -> onOpen(Route.Stats)
                    HomeShortcut.Rewind -> onOpen(Route.Rewind)
                    HomeShortcut.Achievements -> onOpen(Route.Achievements)
                    HomeShortcut.Podcasts -> onOpen(Route.Podcasts)
                    HomeShortcut.Notifications -> onOpen(Route.Notifications)
                    HomeShortcut.Friends -> onOpenFriends()
                }
            },
            onRefresh = {
                listOf(dailyPick, weeklyMix, discoverMix, continueListening, onThisDay, recent, trending, twin, unread)
                    .forEach { it.reload() }
            },
            onPlayStreams = { tracks, index -> container.playStreams(tracks, index) },
            onPlayWeeklyMix = { index, shuffle ->
                val tracks = weeklyMix.state.valueOrNull?.tracks.orEmpty()
                scope.launch {
                    container.player.setShuffle(shuffle)
                    container.player.play(
                        container.weeklyMixPlayables(tracks),
                        if (shuffle) tracks.indices.randomOrNull() ?: 0 else index,
                    )
                }
            },
            onResumeEpisode = { progress -> scope.launch { if (!resumeEpisode(container, progress)) toast("That episode is no longer in its feed.") } },
            onPlayHistory = { entry -> scope.launch { if (!playHistoryEntry(container, entry)) toast("Couldn't find that track.") } },
            onPlayByName = { title, artist -> scope.launch { if (!container.searchAndPlay(title, artist)) toast("No match found for $title.") } },
            onPlayTwinMix = {
                scope.launch {
                    try {
                        val mix = api.twinMix()
                        if (mix.isEmpty()) toast("Your twin's mix is empty right now.") else container.playStreams(mix, 0)
                    } catch (e: Exception) {
                        AppLogger.w("home", "twin mix failed: ${e.javaClass.simpleName}")
                        toast("Couldn't build the Twin Mix.")
                    }
                }
            },
            weeklyMixArtwork = { path -> BridgeUrls.artwork(container.config.baseUrl, path) },
        ),
    )
}

/**
 * Plays something from the account's history, by whichever handle it still has: a cloud
 * path that is in the mirror, a YouTube/SoundCloud page URL, or failing both, its name.
 */
private suspend fun playHistoryEntry(container: AppContainer, entry: HistoryEntryDto): Boolean {
    entry.localSongId?.let { path ->
        container.database.cloudTracks().byPath(path)?.let { track ->
            container.player.play(
                listOf(track.toPlayable(container.config.baseUrl, container.downloads.isDownloaded(path))),
                0,
            )
            return true
        }
    }
    entry.trackUrl?.let { url -> streamFromPageUrl(url, entry.title, entry.artist) }?.let { track ->
        container.playStreams(listOf(track), 0)
        return true
    }
    val title = entry.title ?: return false
    return container.searchAndPlay(title, entry.artist)
}

/** Rebuilds a stream handle from a logged page URL -- the inverse of how a play is logged. */
internal fun streamFromPageUrl(url: String, title: String?, artist: String?): StreamTrackDto? {
    val uri = Uri.parse(url)
    val host = uri.host.orEmpty().lowercase()
    return when {
        "youtube.com" in host -> uri.getQueryParameter("v")?.let { id ->
            StreamTrackDto(id = id, title = title ?: "Unknown", artist = artist.orEmpty(), source = "youtube", youtubeUrl = url)
        }
        host == "youtu.be" -> uri.lastPathSegment?.let { id ->
            StreamTrackDto(id = id, title = title ?: "Unknown", artist = artist.orEmpty(), source = "youtube", youtubeUrl = url)
        }
        "soundcloud.com" in host -> StreamTrackDto(
            id = uri.path.orEmpty().trim('/').replace('/', '-'),
            title = title ?: "Unknown",
            artist = artist.orEmpty(),
            source = "soundcloud",
            youtubeUrl = url,
        )
        else -> null
    }
}

/** Finds the episode in its feed again (progress rows only keep the guid) and resumes it. */
private suspend fun resumeEpisode(container: AppContainer, progress: EpisodeProgressDto): Boolean = try {
    val api = container.http.podcasts
    val episodes = api.episodes(progress.feedUrl)
    val show = api.subscriptions().firstOrNull { it.feedUrl == progress.feedUrl }
    val episode = episodes.firstOrNull { (it.guid ?: it.audioUrl) == progress.episodeGuid }
    val playable = episode?.toPlayable(progress.feedUrl, show?.title ?: "Podcast", show?.artworkUrl)
    if (playable == null) false else {
        container.player.play(listOf(playable), 0, (progress.positionSeconds * 1000).toLong())
        true
    }
} catch (e: Exception) {
    AppLogger.w("home", "resume episode failed: ${e.javaClass.simpleName}")
    false
}

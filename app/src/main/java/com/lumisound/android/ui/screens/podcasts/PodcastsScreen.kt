package com.lumisound.android.ui.screens.podcasts

import android.widget.Toast
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.TrendingUp
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.PlayArrow
import androidx.compose.material.icons.filled.Podcasts
import androidx.compose.material.icons.filled.Search
import androidx.compose.material.icons.filled.SkipNext
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import com.lumisound.android.AppContainer
import com.lumisound.android.bridge.model.EpisodeProgressDto
import com.lumisound.android.bridge.model.PodcastShowDto
import com.lumisound.android.bridge.model.PodcastSubscribeRequest
import com.lumisound.android.bridge.model.PodcastSubscriptionDto
import com.lumisound.android.ui.Route
import com.lumisound.android.ui.components.Artwork
import com.lumisound.android.ui.components.EmptyState
import com.lumisound.android.ui.components.GlassIconButton
import com.lumisound.android.ui.components.IconSectionHeader
import com.lumisound.android.ui.components.Loadable
import com.lumisound.android.ui.components.LoadableSection
import com.lumisound.android.ui.components.LumiCard
import com.lumisound.android.ui.components.OneLine
import com.lumisound.android.ui.components.RingProgress
import com.lumisound.android.ui.components.ScreenTitle
import com.lumisound.android.ui.components.SearchField
import com.lumisound.android.ui.components.friendlyError
import com.lumisound.android.ui.components.rememberLoadable
import com.lumisound.android.ui.theme.LocalLumiPalette
import com.lumisound.android.ui.theme.SectionTint
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.launch

data class PodcastsUiState(
    val query: String = "",
    val submitted: String = "",
    val results: Loadable<List<PodcastShowDto>> = Loadable.Ready(emptyList()),
    val subscriptions: Loadable<List<PodcastSubscriptionDto>> = Loadable.Loading,
    val trending: Loadable<List<PodcastShowDto>> = Loadable.Loading,
    val upNext: Loadable<List<EpisodeProgressDto>> = Loadable.Loading,
)

data class PodcastsCallbacks(
    val onBack: (() -> Unit)? = null,
    val onQuery: (String) -> Unit = {},
    val onSubmit: () -> Unit = {},
    val onOpenShow: (String, String, String?) -> Unit = { _, _, _ -> },
    val onFollow: (PodcastShowDto) -> Unit = {},
    val onResume: (EpisodeProgressDto) -> Unit = {},
    val onRetry: () -> Unit = {},
)

/**
 * The podcast hub, entirely through the bridge: it searches Apple's directory, fetches and
 * parses every feed, and keeps followed shows and episode progress on the account.
 */
@Composable
fun PodcastsScreen(container: AppContainer, onOpen: (Route) -> Unit, onBack: (() -> Unit)?) {
    val scope = rememberCoroutineScope()
    val context = LocalContext.current
    val api = container.http.podcasts
    var query by rememberSaveable { mutableStateOf("") }
    var submitted by rememberSaveable { mutableStateOf("") }
    var searchGeneration by remember { mutableIntStateOf(0) }
    var results by remember { mutableStateOf<Loadable<List<PodcastShowDto>>>(Loadable.Ready(emptyList())) }

    val subscriptions = rememberLoadable(Unit, "podcasts.subs") { api.subscriptions() }
    val trending = rememberLoadable(Unit, "podcasts.trending") { api.trending() }
    val upNext = rememberLoadable(Unit, "podcasts.upNext") { api.progress() }

    LaunchedEffect(submitted, searchGeneration) {
        if (submitted.isBlank()) {
            results = Loadable.Ready(emptyList())
            return@LaunchedEffect
        }
        results = Loadable.Loading
        results = try {
            Loadable.Ready(api.search(submitted))
        } catch (e: CancellationException) {
            throw e
        } catch (e: Exception) {
            Loadable.Failed(friendlyError(e))
        }
    }

    PodcastsContent(
        state = PodcastsUiState(query, submitted, results, subscriptions.state, trending.state, upNext.state),
        callbacks = PodcastsCallbacks(
            onBack = onBack,
            onQuery = { query = it; if (it.isBlank()) submitted = "" },
            onSubmit = { submitted = query.trim() },
            onOpenShow = { feed, title, art -> onOpen(Route.Podcast(feed, title, art)) },
            onFollow = { show ->
                scope.launch {
                    try {
                        api.subscribe(PodcastSubscribeRequest(show.feedUrl))
                        Toast.makeText(context, "Following ${show.title ?: "podcast"}", Toast.LENGTH_SHORT).show()
                        subscriptions.reload()
                        trending.reload()
                    } catch (e: Exception) {
                        Toast.makeText(context, friendlyError(e), Toast.LENGTH_SHORT).show()
                    }
                }
            },
            onResume = { progress ->
                val show = subscriptions.state.valueOrNull?.firstOrNull { it.feedUrl == progress.feedUrl }
                onOpen(Route.Podcast(progress.feedUrl, show?.title ?: progress.title ?: "Podcast", show?.artworkUrl))
            },
            onRetry = { searchGeneration++; subscriptions.reload(); trending.reload() },
        ),
    )
}

/**
 * Podcasts laid out like a shelf of shows rather than a list of feeds: what is in progress
 * first, with a ring showing how far in; the followed shows as a wall of covers; then the
 * chart, ranked with big numerals.
 */
@Composable
fun PodcastsContent(state: PodcastsUiState, callbacks: PodcastsCallbacks) {
    Column(Modifier.fillMaxSize()) {
        ScreenTitle(
            "Podcasts",
            subtitle = "Shows you follow sync with Lumisound",
            eyebrow = "Listen",
            onBack = callbacks.onBack,
        )
        SearchField(state.query, callbacks.onQuery, "Search shows", onSubmit = callbacks.onSubmit)

        LazyColumn(Modifier.fillMaxSize(), contentPadding = PaddingValues(top = 4.dp, bottom = 28.dp)) {
            if (state.submitted.isNotBlank()) {
                item { IconSectionHeader(Icons.Filled.Search, "Results for “${state.submitted}”", tint = SectionTint.Library) }
                item {
                    LoadableSection(state.results, onRetry = callbacks.onRetry) { shows ->
                        if (shows.isEmpty()) {
                            Text("No shows matched.", style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant, modifier = Modifier.padding(horizontal = 20.dp))
                        }
                        Column { shows.forEach { show -> ChartRow(null, show, callbacks) } }
                    }
                }
                return@LazyColumn
            }

            state.upNext.valueOrNull?.takeIf { it.isNotEmpty() }?.let { episodes ->
                item { IconSectionHeader(Icons.Filled.SkipNext, "Up next", tint = SectionTint.Offline) }
                items(episodes.take(3), key = { "${it.feedUrl}#${it.episodeGuid}" }) { episode ->
                    UpNextRow(episode) { callbacks.onResume(episode) }
                }
            }

            item { IconSectionHeader(Icons.Filled.Podcasts, "Your shows", tint = SectionTint.Offline) }
            item {
                LoadableSection(state.subscriptions, onRetry = callbacks.onRetry) { subs ->
                    if (subs.isEmpty()) {
                        EmptyState(
                            icon = Icons.Filled.Podcasts,
                            title = "No shows yet",
                            message = "Search for a show or pick one from the chart below.",
                            modifier = Modifier.height(300.dp),
                        )
                    } else {
                        Column(Modifier.padding(horizontal = 16.dp), verticalArrangement = Arrangement.spacedBy(14.dp)) {
                            subs.chunked(3).forEach { row ->
                                Row(horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                                    row.forEach { sub ->
                                        Column(
                                            Modifier
                                                .weight(1f)
                                                .clip(RoundedCornerShape(8.dp))
                                                .clickable { callbacks.onOpenShow(sub.feedUrl, sub.title ?: "Podcast", sub.artworkUrl) }
                                        ) {
                                            Artwork(sub.artworkUrl, sub.feedUrl, 108.dp, corner = 18.dp)
                                            Spacer(Modifier.height(6.dp))
                                            Text(
                                                sub.title ?: "Podcast",
                                                style = MaterialTheme.typography.labelLarge,
                                                maxLines = 2,
                                                overflow = TextOverflow.Ellipsis,
                                            )
                                        }
                                    }
                                    repeat(3 - row.size) { Spacer(Modifier.weight(1f)) }
                                }
                            }
                        }
                    }
                }
            }

            item { IconSectionHeader(Icons.AutoMirrored.Filled.TrendingUp, "Top charts", tint = SectionTint.Recent) }
            item {
                LoadableSection(state.trending, onRetry = callbacks.onRetry) { shows ->
                    Column { shows.take(15).forEachIndexed { index, show -> ChartRow(index + 1, show, callbacks) } }
                }
            }
        }
    }
}

@Composable
private fun ChartRow(rank: Int?, show: PodcastShowDto, callbacks: PodcastsCallbacks) {
    Row(
        Modifier
            .fillMaxWidth()
            .clickable { callbacks.onOpenShow(show.feedUrl, show.title ?: "Podcast", show.artworkUrl) }
            .padding(start = 16.dp, end = 12.dp, top = 8.dp, bottom = 8.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        rank?.let {
            Text(
                "$it",
                style = MaterialTheme.typography.displaySmall,
                color = if (it <= 3) LocalLumiPalette.current.accent else Color.White.copy(alpha = 0.35f),
                modifier = Modifier.width(48.dp),
            )
        }
        Artwork(show.artworkUrl, show.feedUrl, 60.dp, corner = 14.dp)
        Spacer(Modifier.width(13.dp))
        Column(Modifier.weight(1f)) {
            Text(show.title ?: "Podcast", style = MaterialTheme.typography.titleSmall, maxLines = 2, overflow = TextOverflow.Ellipsis)
            show.artist?.let { OneLine(it, style = MaterialTheme.typography.labelMedium, color = MaterialTheme.colorScheme.onSurfaceVariant) }
        }
        GlassIconButton(Icons.Filled.Add, "Follow", onClick = { callbacks.onFollow(show) })
    }
}

/** An episode in progress: a play button ringed by how far in it is. */
@Composable
private fun UpNextRow(episode: EpisodeProgressDto, onClick: () -> Unit) {
    val fraction = if (episode.durationSeconds > 0) (episode.positionSeconds / episode.durationSeconds).toFloat() else 0f
    val remaining = (episode.durationSeconds - episode.positionSeconds).toInt()
    LumiCard(Modifier.fillMaxWidth().padding(horizontal = 16.dp, vertical = 5.dp), onClick = onClick, corner = 20.dp) {
        Row(Modifier.padding(12.dp), verticalAlignment = Alignment.CenterVertically) {
            Artwork(null, episode.feedUrl, 54.dp, corner = 12.dp)
            Spacer(Modifier.width(12.dp))
            Column(Modifier.weight(1f)) {
                Text(episode.title ?: "Episode", style = MaterialTheme.typography.titleSmall, maxLines = 2, overflow = TextOverflow.Ellipsis)
                if (remaining > 0) {
                    Text("${(remaining + 59) / 60} min left", style = MaterialTheme.typography.labelMedium, color = MaterialTheme.colorScheme.onSurfaceVariant)
                }
            }
            Box(Modifier.size(46.dp), contentAlignment = Alignment.Center) {
                RingProgress(fraction, Modifier.size(46.dp), stroke = 3.dp)
                Icon(Icons.Filled.PlayArrow, contentDescription = "Resume", modifier = Modifier.size(22.dp))
            }
        }
    }
}

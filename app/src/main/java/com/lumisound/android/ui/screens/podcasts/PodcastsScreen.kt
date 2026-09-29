package com.lumisound.android.ui.screens.podcasts

import android.widget.Toast
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.LibraryAdd
import androidx.compose.material.icons.filled.Podcasts
import androidx.compose.material.icons.filled.Search
import androidx.compose.material.icons.filled.TrendingUp
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
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
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.dp
import com.lumisound.android.AppContainer
import com.lumisound.android.bridge.model.PodcastShowDto
import com.lumisound.android.bridge.model.PodcastSubscribeRequest
import com.lumisound.android.ui.Route
import com.lumisound.android.ui.components.Artwork
import com.lumisound.android.ui.components.EmptyState
import com.lumisound.android.ui.components.IconSectionHeader
import com.lumisound.android.ui.components.Loadable
import com.lumisound.android.ui.components.LoadableSection
import com.lumisound.android.ui.components.OneLine
import com.lumisound.android.ui.components.SearchField
import com.lumisound.android.ui.components.ShelfCard
import com.lumisound.android.ui.components.friendlyError
import com.lumisound.android.ui.components.rememberLoadable
import com.lumisound.android.ui.screens.stats.DetailHeader
import com.lumisound.android.ui.theme.SectionTint
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.launch

/**
 * The podcast hub: shows this account follows (shared with Lumisound), Apple's trending
 * chart minus those, and search across Apple's directory -- all through the bridge, which
 * fetches and parses every feed so the app never touches RSS itself.
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

    fun open(show: PodcastShowDto) = onOpen(Route.Podcast(show.feedUrl, show.title ?: "Podcast", show.artworkUrl))
    fun subscribe(show: PodcastShowDto) {
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
    }

    Column(Modifier.fillMaxSize()) {
        if (onBack != null) {
            DetailHeader("Podcasts", "Followed shows sync with Lumisound", onBack)
        } else {
            Text(
                "Podcasts",
                style = MaterialTheme.typography.displaySmall,
                modifier = Modifier.padding(start = 20.dp, top = 12.dp, bottom = 10.dp),
            )
        }
        SearchField(
            value = query,
            onValueChange = { query = it; if (it.isBlank()) submitted = "" },
            placeholder = "Search podcasts…",
            onSubmit = { submitted = query.trim() },
        )

        LazyColumn(Modifier.fillMaxSize(), contentPadding = PaddingValues(top = 8.dp, bottom = 28.dp)) {
            if (submitted.isNotBlank()) {
                item { IconSectionHeader(Icons.Filled.Search, "Results for “$submitted”", tint = SectionTint.Library) }
                item {
                    LoadableSection(results, onRetry = { searchGeneration++ }) { shows ->
                        if (shows.isEmpty()) {
                            Text(
                                "No podcasts matched.",
                                style = MaterialTheme.typography.bodySmall,
                                color = MaterialTheme.colorScheme.onSurfaceVariant,
                                modifier = Modifier.padding(horizontal = 20.dp, vertical = 8.dp),
                            )
                        }
                        Column { shows.forEach { show -> ShowRow(show, onClick = { open(show) }, onFollow = { subscribe(show) }) } }
                    }
                }
                return@LazyColumn
            }

            item { IconSectionHeader(Icons.Filled.Podcasts, "Following", tint = SectionTint.Offline) }
            item {
                LoadableSection(subscriptions.state, onRetry = subscriptions::reload) { subs ->
                    if (subs.isEmpty()) {
                        EmptyState(
                            icon = Icons.Filled.Podcasts,
                            title = "No shows yet",
                            message = "Search for a show or pick one from the chart below. Shows you follow here also appear in Lumisound.",
                            modifier = Modifier.padding(vertical = 4.dp).fillMaxWidth(),
                        )
                    } else {
                        LazyRow(contentPadding = PaddingValues(horizontal = 14.dp)) {
                            items(subs, key = { it.id }) { sub ->
                                ShelfCard(
                                    title = sub.title ?: "Podcast",
                                    subtitle = "Following",
                                    artworkModel = sub.artworkUrl,
                                    fallbackKey = sub.feedUrl,
                                    onClick = { onOpen(Route.Podcast(sub.feedUrl, sub.title ?: "Podcast", sub.artworkUrl)) },
                                )
                            }
                        }
                    }
                }
            }

            item { IconSectionHeader(Icons.Filled.TrendingUp, "Trending", tint = SectionTint.Recent) }
            item {
                LoadableSection(trending.state, onRetry = trending::reload) { shows ->
                    Column { shows.forEach { show -> ShowRow(show, onClick = { open(show) }, onFollow = { subscribe(show) }) } }
                }
            }
        }
    }
}

@Composable
private fun ShowRow(show: PodcastShowDto, onClick: () -> Unit, onFollow: () -> Unit) {
    Row(
        Modifier.fillMaxWidth().clickable(onClick = onClick).padding(start = 20.dp, end = 8.dp, top = 8.dp, bottom = 8.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(13.dp),
    ) {
        Artwork(model = show.artworkUrl, fallbackKey = show.feedUrl, size = 56.dp, corner = 12.dp)
        Column(Modifier.weight(1f)) {
            OneLine(show.title ?: "Podcast", style = MaterialTheme.typography.bodyLarge)
            show.artist?.let {
                OneLine(it, style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
            }
        }
        IconButton(onClick = onFollow) {
            Icon(Icons.Filled.LibraryAdd, contentDescription = "Follow", tint = MaterialTheme.colorScheme.onSurfaceVariant)
        }
    }
}

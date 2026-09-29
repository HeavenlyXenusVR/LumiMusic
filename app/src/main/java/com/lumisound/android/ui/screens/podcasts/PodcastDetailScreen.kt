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
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.CheckCircle
import androidx.compose.material.icons.filled.GraphicEq
import androidx.compose.material.icons.filled.MoreVert
import androidx.compose.material3.Button
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.lumisound.android.AppContainer
import com.lumisound.android.bridge.model.EpisodeProgressDto
import com.lumisound.android.bridge.model.PodcastEpisodeDto
import com.lumisound.android.bridge.model.PodcastSubscribeRequest
import com.lumisound.android.playback.toPlayable
import com.lumisound.android.ui.components.Artwork
import com.lumisound.android.ui.components.LoadableSection
import com.lumisound.android.ui.components.OneLine
import com.lumisound.android.ui.components.friendlyError
import com.lumisound.android.ui.components.rememberLoadable
import com.lumisound.android.ui.screens.social.parseInstant
import com.lumisound.android.ui.screens.stats.DetailHeader
import com.lumisound.android.ui.screens.stats.ProgressLine
import com.lumisound.android.ui.theme.LocalLumiPalette
import kotlinx.coroutines.launch
import java.time.format.DateTimeFormatter
import java.time.format.FormatStyle

/**
 * One show: follow or unfollow it, and its episodes with how far into each this account
 * has got. Tapping an episode resumes it where it was left, on this device or the phone.
 */
@Composable
fun PodcastDetailScreen(
    container: AppContainer,
    feedUrl: String,
    title: String,
    artworkUrl: String?,
    onBack: () -> Unit,
) {
    val scope = rememberCoroutineScope()
    val context = LocalContext.current
    val api = container.http.podcasts
    val playback by container.player.state.collectAsStateWithLifecycle()

    val episodes = rememberLoadable(feedUrl, "podcast.episodes") { api.episodes(feedUrl) }
    val progress = rememberLoadable(feedUrl, "podcast.progress") {
        api.progress(feedUrl = feedUrl, limit = 200).associateBy { it.episodeGuid }
    }
    val subscription = rememberLoadable(feedUrl, "podcast.sub") { api.subscriptions().firstOrNull { it.feedUrl == feedUrl } }
    var menuFor by remember { mutableStateOf<String?>(null) }

    fun toast(text: String) = Toast.makeText(context, text, Toast.LENGTH_SHORT).show()

    fun play(list: List<PodcastEpisodeDto>, episode: PodcastEpisodeDto, fromStart: Boolean) {
        val playables = list.mapNotNull { it.toPlayable(feedUrl, title, artworkUrl) }
        val guid = episode.guid ?: episode.audioUrl
        val index = playables.indexOfFirst { it.episodeGuid == guid }
        if (index < 0) {
            toast("This episode has no audio file.")
            return
        }
        val saved = guid?.let { progress.state.valueOrNull?.get(it) }
        val resumeAt = if (fromStart || saved == null || saved.completed) 0L else (saved.positionSeconds * 1000).toLong()
        container.player.setShuffle(false)
        container.player.play(playables, index, resumeAt)
    }

    Column(Modifier.fillMaxSize()) {
        DetailHeader(title, null, onBack)
        Row(
            Modifier.fillMaxWidth().padding(horizontal = 20.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(16.dp),
        ) {
            Artwork(model = artworkUrl, fallbackKey = feedUrl, size = 96.dp, corner = 16.dp)
            Column(Modifier.weight(1f)) {
                val count = episodes.state.valueOrNull?.size
                Text(
                    count?.let { "$it episodes" } ?: "Loading episodes…",
                    style = MaterialTheme.typography.labelMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
                Spacer(Modifier.height(8.dp))
                val sub = subscription.state.valueOrNull
                if (sub != null) {
                    OutlinedButton(onClick = {
                        scope.launch {
                            try {
                                api.unsubscribe(sub.id)
                                subscription.reload()
                                toast("Unfollowed $title")
                            } catch (e: Exception) {
                                toast(friendlyError(e))
                            }
                        }
                    }) { Text("Following") }
                } else {
                    Button(onClick = {
                        scope.launch {
                            try {
                                api.subscribe(PodcastSubscribeRequest(feedUrl))
                                subscription.reload()
                                toast("Following $title")
                            } catch (e: Exception) {
                                toast(friendlyError(e))
                            }
                        }
                    }) { Text("Follow") }
                }
            }
        }
        Spacer(Modifier.height(10.dp))

        LoadableSection(episodes.state, onRetry = episodes::reload) { list ->
            val saved = progress.state.valueOrNull.orEmpty()
            LazyColumn(Modifier.fillMaxSize(), contentPadding = PaddingValues(bottom = 28.dp)) {
                // Indexed keys: feeds repeat guids and titles more often than they should, and a
                // duplicate key crashes a lazy list outright.
                itemsIndexed(list, key = { index, it -> "$index|${it.guid ?: it.audioUrl}" }) { _, episode ->
                    val guid = episode.guid ?: episode.audioUrl
                    val isPlaying = guid != null && playback.episodeGuid == guid
                    Box {
                        EpisodeRow(
                            episode = episode,
                            progress = guid?.let { saved[it] },
                            isPlaying = isPlaying,
                            onClick = { play(list, episode, fromStart = false) },
                            onMenu = { menuFor = guid },
                        )
                        DropdownMenu(expanded = menuFor != null && menuFor == guid, onDismissRequest = { menuFor = null }) {
                            DropdownMenuItem(text = { Text("Play from the start") }, onClick = {
                                menuFor = null
                                play(list, episode, fromStart = true)
                            })
                            DropdownMenuItem(text = { Text("Play next") }, onClick = {
                                menuFor = null
                                episode.toPlayable(feedUrl, title, artworkUrl)?.let { container.player.playNext(it) }
                            })
                        }
                    }
                }
            }
        }
    }
}

@Composable
private fun EpisodeRow(
    episode: PodcastEpisodeDto,
    progress: EpisodeProgressDto?,
    isPlaying: Boolean,
    onClick: () -> Unit,
    onMenu: () -> Unit,
) {
    val palette = LocalLumiPalette.current
    Row(
        Modifier.fillMaxWidth().clickable(onClick = onClick).padding(start = 20.dp, end = 8.dp, top = 10.dp, bottom = 10.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(12.dp),
    ) {
        Column(Modifier.weight(1f)) {
            val date = parseInstant(episode.publishedAt)?.format(DateTimeFormatter.ofLocalizedDate(FormatStyle.MEDIUM))
            val length = episode.durationSeconds?.takeIf { it > 0 }?.let { "${(it + 59) / 60} min" }
            Text(
                listOfNotNull(date, length).joinToString(" · "),
                style = MaterialTheme.typography.labelSmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
            Text(
                episode.title,
                style = MaterialTheme.typography.bodyLarge,
                color = if (isPlaying) palette.accent else MaterialTheme.colorScheme.onSurface,
                maxLines = 2,
                overflow = TextOverflow.Ellipsis,
            )
            val summary = stripHtml(episode.description)
            if (summary.isNotBlank()) {
                OneLine(summary, style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
            }
            if (progress != null && !progress.completed && progress.durationSeconds > 0) {
                Spacer(Modifier.height(6.dp))
                ProgressLine((progress.positionSeconds / progress.durationSeconds).toFloat())
            }
        }
        when {
            isPlaying -> Icon(Icons.Filled.GraphicEq, contentDescription = "Playing", tint = palette.accent)
            progress?.completed == true -> Icon(Icons.Filled.CheckCircle, contentDescription = "Played", tint = MaterialTheme.colorScheme.onSurfaceVariant)
            else -> Unit
        }
        IconButton(onClick = onMenu) {
            Icon(Icons.Filled.MoreVert, contentDescription = "More", tint = MaterialTheme.colorScheme.onSurfaceVariant)
        }
    }
}

/** Feed descriptions are HTML; a one-line teaser wants the words only. */
fun stripHtml(html: String): String =
    html.replace(Regex("<[^>]*>"), " ")
        .replace("&nbsp;", " ")
        .replace("&amp;", "&")
        .replace("&quot;", "\"")
        .replace("&#39;", "'")
        .replace("&lt;", "<")
        .replace("&gt;", ">")
        .replace(Regex("\\s+"), " ")
        .trim()

package com.lumisound.android.ui.screens.social

import android.widget.Toast
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Favorite
import androidx.compose.material.icons.filled.GraphicEq
import androidx.compose.material.icons.filled.Inbox
import androidx.compose.material.icons.filled.People
import androidx.compose.material.icons.filled.PersonAdd
import androidx.compose.material.icons.filled.PersonSearch
import androidx.compose.material.icons.filled.Refresh
import androidx.compose.material.icons.filled.Timeline
import androidx.compose.material3.Button
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
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
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import coil3.compose.AsyncImage
import com.lumisound.android.AppContainer
import com.lumisound.android.bridge.BridgeUrls
import com.lumisound.android.bridge.model.FriendActivityDto
import com.lumisound.android.bridge.model.FriendDto
import com.lumisound.android.bridge.model.FriendRequestCreate
import com.lumisound.android.bridge.model.FriendRequestDto
import com.lumisound.android.bridge.model.PresenceDto
import com.lumisound.android.bridge.model.PublicUserDto
import com.lumisound.android.ui.Route
import com.lumisound.android.ui.components.CapsuleToolbar
import com.lumisound.android.ui.components.ChipRow
import com.lumisound.android.ui.components.EmptyState
import com.lumisound.android.ui.components.FallbackArt
import com.lumisound.android.ui.components.Loadable
import com.lumisound.android.ui.components.LoadableSection
import com.lumisound.android.ui.components.NavChip
import com.lumisound.android.ui.components.OneLine
import com.lumisound.android.ui.components.Pill
import com.lumisound.android.ui.components.ScreenTitle
import com.lumisound.android.ui.components.SearchField
import com.lumisound.android.ui.components.ToolbarAction
import com.lumisound.android.ui.components.friendlyError
import com.lumisound.android.ui.components.rememberLoadable
import com.lumisound.android.ui.searchAndPlay
import com.lumisound.android.ui.theme.LocalLumiPalette
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import java.time.Duration
import java.time.OffsetDateTime

enum class SocialTab(val label: String) { Friends("Friends"), Requests("Requests"), Activity("Activity"), Find("Find people") }

/** A friend with whatever presence the bridge reported for them. */
data class FriendWithPresence(val friend: FriendDto, val presence: PresenceDto?)

/**
 * Friends, the same friend graph Lumisound uses: who is online and what they are playing
 * right now, incoming requests, a feed of what friends played and favorited, and search
 * for new people. Presence is refreshed every thirty seconds while this screen is open --
 * the bridge's own freshness window is ninety.
 */
@Composable
fun SocialScreen(container: AppContainer, onOpen: (Route) -> Unit) {
    val scope = rememberCoroutineScope()
    val context = LocalContext.current
    val api = container.http.social
    var tab by rememberSaveable { mutableStateOf(SocialTab.Friends) }
    var query by rememberSaveable { mutableStateOf("") }
    var searchResults by remember { mutableStateOf<Loadable<List<PublicUserDto>>>(Loadable.Ready(emptyList())) }
    var sentTo by remember { mutableStateOf(setOf<String>()) }
    var searchGeneration by remember { mutableIntStateOf(0) }

    val friends = rememberLoadable(Unit, "social.friends") { api.friends().friends }
    val presence = rememberLoadable(Unit, "social.presence") { api.friendsPresence().presence }
    val requests = rememberLoadable(Unit, "social.requests") { api.requests() }
    val activity = rememberLoadable(Unit, "social.activity") { api.friendsActivity().activity }

    LaunchedEffect(Unit) {
        while (true) {
            delay(30_000)
            presence.reload()
        }
    }
    LaunchedEffect(query, searchGeneration) {
        val q = query.trim()
        if (q.length < 2) {
            searchResults = Loadable.Ready(emptyList())
            return@LaunchedEffect
        }
        delay(350)
        searchResults = Loadable.Loading
        searchResults = try {
            Loadable.Ready(api.searchUsers(q).users)
        } catch (e: kotlinx.coroutines.CancellationException) {
            throw e
        } catch (e: Exception) {
            Loadable.Failed(friendlyError(e))
        }
    }

    fun toast(text: String) = Toast.makeText(context, text, Toast.LENGTH_SHORT).show()
    fun act(done: String, block: suspend () -> Unit) {
        scope.launch {
            try {
                block()
                toast(done)
                requests.reload()
                friends.reload()
                presence.reload()
            } catch (e: Exception) {
                toast(friendlyError(e))
            }
        }
    }

    val incoming = requests.state.valueOrNull?.incoming.orEmpty()
    val presenceById = presence.state.valueOrNull.orEmpty().associateBy { it.userId }
    val merged: Loadable<List<FriendWithPresence>> = when (val f = friends.state) {
        is Loadable.Ready -> Loadable.Ready(
            f.value.map { FriendWithPresence(it, presenceById[it.userId]) }
                // Listening first, then online, then everyone else alphabetically.
                .sortedWith(
                    compareByDescending<FriendWithPresence> { it.presence?.isPlaying == true }
                        .thenByDescending { it.presence?.online == true }
                        .thenBy { it.friend.shownName.lowercase() }
                )
        )
        is Loadable.Failed -> f
        Loadable.Loading -> Loadable.Loading
    }

    Column(Modifier.fillMaxSize()) {
        Box(Modifier.fillMaxWidth(), contentAlignment = Alignment.Center) {
            CapsuleToolbar(
                listOf(
                    ToolbarAction(Icons.Filled.Refresh, "Refresh", onClick = {
                        listOf(friends, presence, requests, activity).forEach { it.reload() }
                    }),
                    ToolbarAction(Icons.Filled.PersonAdd, "Find people", selected = tab == SocialTab.Find, onClick = { tab = SocialTab.Find }),
                )
            )
        }
        val online = presenceById.values.count { it.online }
        ScreenTitle(
            "Friends",
            subtitle = friends.state.valueOrNull?.let { "${it.size} friends · $online online" } ?: "Listening together, apart",
        )
        ChipRow(
            SocialTab.entries.map { entry ->
                NavChip(
                    label = if (entry == SocialTab.Requests && incoming.isNotEmpty()) "${entry.label} (${incoming.size})" else entry.label,
                    icon = when (entry) {
                        SocialTab.Friends -> Icons.Filled.People
                        SocialTab.Requests -> Icons.Filled.Inbox
                        SocialTab.Activity -> Icons.Filled.Timeline
                        SocialTab.Find -> Icons.Filled.PersonSearch
                    },
                    selected = tab == entry,
                    onClick = { tab = entry },
                )
            }
        )

        val avatar = { userId: String -> BridgeUrls.avatar(container.config.baseUrl, userId) }
        when (tab) {
            SocialTab.Friends -> LoadableSection(merged, onRetry = friends::reload) { list ->
                if (list.isEmpty()) {
                    EmptyState(
                        icon = Icons.Filled.People,
                        title = "No friends yet",
                        message = "Find people by username to see what they're listening to, compare your taste, and play a blend of both.",
                        action = { Button(onClick = { tab = SocialTab.Find }) { Text("Find people") } },
                    )
                } else {
                    LazyColumn(Modifier.fillMaxSize(), contentPadding = PaddingValues(bottom = 24.dp)) {
                        items(list, key = { it.friend.userId }) { entry ->
                            val title = entry.presence?.nowPlayingTitle
                            val playTheirs: (() -> Unit)? = if (title == null) null else {
                                {
                                    scope.launch {
                                        if (!container.searchAndPlay(title, entry.presence?.nowPlayingArtist)) toast("No match for $title.")
                                    }
                                }
                            }
                            FriendRow(
                                entry = entry,
                                avatarUrl = avatar(entry.friend.userId),
                                onClick = { onOpen(Route.Profile(entry.friend.userId, entry.friend.shownName)) },
                                onPlayTheirs = playTheirs,
                            )
                        }
                    }
                }
            }

            SocialTab.Requests -> LoadableSection(requests.state, onRetry = requests::reload) { data ->
                if (data.incoming.isEmpty() && data.outgoing.isEmpty()) {
                    EmptyState(Icons.Filled.Inbox, "No pending requests", "Requests you send and receive show up here.")
                } else {
                    LazyColumn(Modifier.fillMaxSize(), contentPadding = PaddingValues(bottom = 24.dp)) {
                        if (data.incoming.isNotEmpty()) item { SectionLabel("Wants to be friends") }
                        items(data.incoming, key = { "in-${it.requestId}" }) { request ->
                            RequestRow(request, avatar(request.userId)) {
                                OutlinedButton(onClick = { act("Declined") { api.decline(request.requestId) } }) { Text("Decline") }
                                Button(onClick = { act("You're now friends with ${request.shownName}") { api.accept(request.requestId) } }) { Text("Accept") }
                            }
                        }
                        if (data.outgoing.isNotEmpty()) item { SectionLabel("Sent") }
                        items(data.outgoing, key = { "out-${it.requestId}" }) { request ->
                            RequestRow(request, avatar(request.userId)) {
                                OutlinedButton(onClick = { act("Request cancelled") { api.cancel(request.requestId) } }) { Text("Cancel") }
                            }
                        }
                    }
                }
            }

            SocialTab.Activity -> LoadableSection(activity.state, onRetry = activity::reload) { rows ->
                if (rows.isEmpty()) {
                    EmptyState(Icons.Filled.Timeline, "Quiet in here", "When friends play or favorite something, it shows up here.")
                } else {
                    LazyColumn(Modifier.fillMaxSize(), contentPadding = PaddingValues(bottom = 24.dp)) {
                        items(rows.size) { index ->
                            val row = rows[index]
                            ActivityRow(row, avatar(row.userId), onClick = {
                                val title = row.title ?: return@ActivityRow
                                scope.launch { if (!container.searchAndPlay(title, row.artist)) toast("No match for $title.") }
                            })
                        }
                    }
                }
            }

            SocialTab.Find -> Column(Modifier.fillMaxSize()) {
                SearchField(query, { query = it }, "Search by username…")
                LoadableSection(searchResults, onRetry = { searchGeneration++ }) { users ->
                    val friendIds = friends.state.valueOrNull.orEmpty().map { it.userId }.toSet()
                    val pendingIds = requests.state.valueOrNull?.outgoing.orEmpty().map { it.userId }.toSet() + sentTo
                    LazyColumn(Modifier.fillMaxSize(), contentPadding = PaddingValues(vertical = 8.dp)) {
                        items(users, key = { it.userId }) { user ->
                            Row(
                                Modifier
                                    .fillMaxWidth()
                                    .clickable { onOpen(Route.Profile(user.userId, user.shownName)) }
                                    .padding(horizontal = 20.dp, vertical = 10.dp),
                                verticalAlignment = Alignment.CenterVertically,
                                horizontalArrangement = Arrangement.spacedBy(12.dp),
                            ) {
                                Avatar(avatar(user.userId), user.username, 44.dp)
                                Column(Modifier.weight(1f)) {
                                    OneLine(user.shownName, style = MaterialTheme.typography.bodyLarge)
                                    OneLine("@${user.username}", style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                                }
                                when {
                                    user.userId in friendIds -> Pill("Friends")
                                    user.userId in pendingIds -> Pill("Requested")
                                    else -> Button(onClick = {
                                        sentTo = sentTo + user.userId
                                        act("Request sent to ${user.shownName}") { api.sendRequest(FriendRequestCreate(toUserId = user.userId)) }
                                    }) { Text("Add") }
                                }
                            }
                        }
                    }
                    if (users.isEmpty() && query.trim().length >= 2) {
                        Text(
                            "No one by that name.",
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                            modifier = Modifier.padding(24.dp),
                        )
                    }
                }
            }
        }
    }
}

@Composable
private fun SectionLabel(text: String) {
    Text(
        text.uppercase(),
        style = MaterialTheme.typography.labelSmall,
        color = MaterialTheme.colorScheme.onSurfaceVariant,
        modifier = Modifier.padding(start = 20.dp, top = 14.dp, bottom = 6.dp),
    )
}

/** A circular avatar from the bridge, with the generated art underneath for anyone without one. */
@Composable
fun Avatar(url: String?, fallbackKey: String, size: Dp, online: Boolean? = null) {
    val palette = LocalLumiPalette.current
    Box {
        Box(Modifier.size(size).clip(CircleShape)) {
            FallbackArt(fallbackKey, Modifier.fillMaxSize())
            if (url != null) {
                AsyncImage(model = url, contentDescription = null, contentScale = ContentScale.Crop, modifier = Modifier.fillMaxSize())
            }
        }
        if (online == true) {
            Box(
                Modifier
                    .align(Alignment.BottomEnd)
                    .size(size / 4)
                    .clip(CircleShape)
                    .background(palette.pageBottom)
                    .padding(2.dp)
                    .clip(CircleShape)
                    .background(Color(0xFF48BB78))
            )
        }
    }
}

@Composable
private fun FriendRow(entry: FriendWithPresence, avatarUrl: String, onClick: () -> Unit, onPlayTheirs: (() -> Unit)?) {
    val palette = LocalLumiPalette.current
    val presence = entry.presence
    Row(
        Modifier.fillMaxWidth().clickable(onClick = onClick).padding(horizontal = 20.dp, vertical = 10.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(12.dp),
    ) {
        Avatar(avatarUrl, entry.friend.username, 48.dp, online = presence?.online)
        Column(Modifier.weight(1f)) {
            OneLine(entry.friend.shownName, style = MaterialTheme.typography.bodyLarge)
            val line = when {
                presence?.isPlaying == true && presence.nowPlayingTitle != null ->
                    "♪ ${presence.nowPlayingTitle}" + (presence.nowPlayingArtist?.let { " · $it" } ?: "")
                presence?.online == true -> "Online"
                else -> lastSeen(presence?.lastSeenAt)
            }
            OneLine(
                line,
                style = MaterialTheme.typography.labelSmall,
                color = if (presence?.isPlaying == true) palette.accent else MaterialTheme.colorScheme.onSurfaceVariant,
            )
        }
        if (onPlayTheirs != null) {
            Box(
                Modifier.size(34.dp).clip(CircleShape).background(palette.accentWash).clickable(onClick = onPlayTheirs),
                contentAlignment = Alignment.Center,
            ) {
                Icon(Icons.Filled.GraphicEq, contentDescription = "Play what they're playing", tint = palette.accent, modifier = Modifier.size(18.dp))
            }
        }
    }
}

@Composable
private fun RequestRow(request: FriendRequestDto, avatarUrl: String, actions: @Composable () -> Unit) {
    Row(
        Modifier.fillMaxWidth().padding(horizontal = 20.dp, vertical = 10.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(10.dp),
    ) {
        Avatar(avatarUrl, request.username, 44.dp)
        Column(Modifier.weight(1f)) {
            OneLine(request.shownName, style = MaterialTheme.typography.bodyLarge)
            OneLine("@${request.username}", style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
        }
        actions()
    }
}

@Composable
private fun ActivityRow(row: FriendActivityDto, avatarUrl: String, onClick: () -> Unit) {
    val palette = LocalLumiPalette.current
    Row(
        Modifier.fillMaxWidth().clickable(onClick = onClick).padding(horizontal = 20.dp, vertical = 10.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(12.dp),
    ) {
        Avatar(avatarUrl, row.username, 40.dp)
        Column(Modifier.weight(1f)) {
            Text(
                "${row.shownName} ${if (row.kind == "favorited") "favorited" else "played"}",
                style = MaterialTheme.typography.labelMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
            OneLine(
                listOfNotNull(row.title, row.artist?.takeIf { it.isNotBlank() }).joinToString(" · "),
                style = MaterialTheme.typography.bodyMedium,
            )
        }
        if (row.kind == "favorited") {
            Icon(Icons.Filled.Favorite, contentDescription = null, tint = palette.accent, modifier = Modifier.size(16.dp))
        }
        Text(timeAgo(row.at), style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
    }
}

/** "5m", "3h", "2d" from an ISO timestamp; blank when there is none or it will not parse. */
fun timeAgo(iso: String?, now: OffsetDateTime = OffsetDateTime.now()): String {
    val then = parseInstant(iso) ?: return ""
    val minutes = Duration.between(then, now).toMinutes().coerceAtLeast(0)
    return when {
        minutes < 1 -> "now"
        minutes < 60 -> "${minutes}m"
        minutes < 60 * 24 -> "${minutes / 60}h"
        minutes < 60 * 24 * 30 -> "${minutes / (60 * 24)}d"
        else -> "${minutes / (60 * 24 * 30)}mo"
    }
}

private fun lastSeen(iso: String?): String = timeAgo(iso).let { if (it.isEmpty()) "Offline" else "Seen $it ago".replace("Seen now ago", "Just now") }

/**
 * The bridge writes timestamps with Python's `isoformat()`, which leaves off the offset
 * for naive (UTC) values. Both shapes are accepted, the naive one read as UTC.
 */
fun parseInstant(iso: String?): OffsetDateTime? {
    if (iso.isNullOrBlank()) return null
    return runCatching { OffsetDateTime.parse(iso) }.getOrNull()
        ?: runCatching { java.time.LocalDateTime.parse(iso).atOffset(java.time.ZoneOffset.UTC) }.getOrNull()
}

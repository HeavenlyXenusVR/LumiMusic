package com.lumisound.android.ui.screens.notifications

import android.widget.Toast
import androidx.compose.foundation.background
import androidx.compose.foundation.border
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
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Campaign
import androidx.compose.material.icons.filled.DoneAll
import androidx.compose.material.icons.filled.EmojiEvents
import androidx.compose.material.icons.filled.Group
import androidx.compose.material.icons.filled.NewReleases
import androidx.compose.material.icons.filled.Notifications
import androidx.compose.material.icons.filled.PersonAdd
import androidx.compose.material.icons.filled.Podcasts
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.dp
import com.lumisound.android.AppContainer
import com.lumisound.android.bridge.model.NotificationDto
import com.lumisound.android.ui.components.EmptyState
import com.lumisound.android.ui.components.Eyebrow
import com.lumisound.android.ui.components.GlassButton
import com.lumisound.android.ui.components.Loadable
import com.lumisound.android.ui.components.LoadableSection
import com.lumisound.android.ui.components.ScreenTitle
import com.lumisound.android.ui.components.friendlyError
import com.lumisound.android.ui.components.rememberLoadable
import com.lumisound.android.ui.screens.social.parseInstant
import com.lumisound.android.ui.screens.social.timeAgo
import com.lumisound.android.ui.theme.LocalLumiPalette
import com.lumisound.android.ui.theme.SectionTint
import kotlinx.coroutines.launch
import java.time.Duration
import java.time.OffsetDateTime

@Composable
fun NotificationsScreen(container: AppContainer, onBack: () -> Unit) {
    val scope = rememberCoroutineScope()
    val context = LocalContext.current
    val api = container.http.discovery
    val notifications = rememberLoadable(Unit, "notifications") { api.notifications(limit = 100) }
    // Marked read optimistically, so a tap changes the card at once rather than after a reload.
    var readLocally by remember { mutableStateOf(setOf<String>()) }

    NotificationsContent(
        notifications = notifications.state,
        readLocally = readLocally,
        onBack = onBack,
        onRetry = notifications::reload,
        onOpen = { item ->
            if (item.isUnread && item.id !in readLocally) {
                readLocally = readLocally + item.id
                scope.launch { runCatching { api.markNotificationRead(item.id) } }
            }
        },
        onReadAll = {
            scope.launch {
                try {
                    api.markAllNotificationsRead()
                    readLocally = notifications.state.valueOrNull.orEmpty().map { it.id }.toSet()
                } catch (e: Exception) {
                    Toast.makeText(context, friendlyError(e), Toast.LENGTH_SHORT).show()
                }
            }
        },
    )
}

/**
 * The account's inbox -- friend requests, achievement unlocks, new uploads from followed
 * channels, new podcast episodes, collaborator activity -- grouped into Today, This week and
 * Earlier, each kind with its own colour so the page can be read by colour before words.
 * The same rows Lumisound's inbox reads, so reading one here marks it read there too.
 */
@Composable
fun NotificationsContent(
    notifications: Loadable<List<NotificationDto>>,
    readLocally: Set<String>,
    onBack: () -> Unit,
    onRetry: () -> Unit,
    onOpen: (NotificationDto) -> Unit,
    onReadAll: () -> Unit,
    now: OffsetDateTime = OffsetDateTime.now(),
) {
    val list = notifications.valueOrNull.orEmpty()
    val unread = list.count { it.isUnread && it.id !in readLocally }
    Column(Modifier.fillMaxSize()) {
        ScreenTitle(
            "Inbox",
            subtitle = if (unread > 0) "$unread unread" else "All caught up",
            eyebrow = "Notifications",
            onBack = onBack,
            trailing = { if (unread > 0) GlassButton("Read all", Icons.Filled.DoneAll, onClick = onReadAll) },
        )
        LoadableSection(notifications, onRetry = onRetry) { items ->
            if (items.isEmpty()) {
                EmptyState(Icons.Filled.Notifications, "All caught up", "Friend requests, badges and new releases will show up here.")
                return@LoadableSection
            }
            val groups = items.groupBy { bucket(it.createdAt, now) }
            LazyColumn(Modifier.fillMaxSize(), contentPadding = PaddingValues(bottom = 28.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                listOf("Today", "This week", "Earlier").forEach { label ->
                    val group = groups[label].orEmpty()
                    if (group.isEmpty()) return@forEach
                    item(key = "h-$label") { Eyebrow(label, Modifier.padding(start = 20.dp, top = 12.dp, bottom = 2.dp)) }
                    group.forEach { note ->
                        item(key = note.id) {
                            NotificationCard(note, unread = note.isUnread && note.id !in readLocally, now = now) { onOpen(note) }
                        }
                    }
                }
            }
        }
    }
}

private fun bucket(iso: String?, now: OffsetDateTime): String {
    val then = parseInstant(iso) ?: return "Earlier"
    val hours = Duration.between(then, now).toHours()
    return when {
        hours < 24 -> "Today"
        hours < 24 * 7 -> "This week"
        else -> "Earlier"
    }
}

private data class Kind(val icon: ImageVector, val tint: Color)

private fun kindFor(type: String): Kind = when {
    type.startsWith("friend") -> Kind(Icons.Filled.PersonAdd, SectionTint.Recent)
    type.startsWith("achievement") -> Kind(Icons.Filled.EmojiEvents, SectionTint.Device)
    type.startsWith("subscription") -> Kind(Icons.Filled.NewReleases, SectionTint.Library)
    type.startsWith("podcast") -> Kind(Icons.Filled.Podcasts, SectionTint.Offline)
    type.startsWith("collab") || type.startsWith("playlist") -> Kind(Icons.Filled.Group, SectionTint.Playlists)
    type.startsWith("announcement") -> Kind(Icons.Filled.Campaign, SectionTint.Favorites)
    else -> Kind(Icons.Filled.Notifications, SectionTint.Favorites)
}

@Composable
private fun NotificationCard(item: NotificationDto, unread: Boolean, now: OffsetDateTime, onClick: () -> Unit) {
    val palette = LocalLumiPalette.current
    val kind = kindFor(item.type)
    val shape = RoundedCornerShape(20.dp)
    Row(
        Modifier
            .fillMaxWidth()
            .padding(horizontal = 16.dp)
            .clip(shape)
            .background(if (unread) kind.tint.copy(alpha = 0.12f) else palette.elevatedSurface)
            .border(1.dp, if (unread) kind.tint.copy(alpha = 0.35f) else palette.hairline, shape)
            .clickable(onClick = onClick)
            .padding(14.dp),
        verticalAlignment = Alignment.Top,
        horizontalArrangement = Arrangement.spacedBy(13.dp),
    ) {
        Box(Modifier.size(42.dp).clip(CircleShape).background(kind.tint.copy(alpha = 0.22f)), contentAlignment = Alignment.Center) {
            Icon(kind.icon, contentDescription = null, tint = kind.tint, modifier = Modifier.size(21.dp))
        }
        Column(Modifier.weight(1f)) {
            Text(item.title ?: "Notification", style = MaterialTheme.typography.titleSmall)
            item.body?.takeIf { it.isNotBlank() }?.let {
                Text(it, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
            }
        }
        Column(horizontalAlignment = Alignment.End) {
            Text(timeAgo(item.createdAt, now), style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
            if (unread) Box(Modifier.padding(top = 8.dp).size(9.dp).clip(CircleShape).background(kind.tint))
        }
    }
}

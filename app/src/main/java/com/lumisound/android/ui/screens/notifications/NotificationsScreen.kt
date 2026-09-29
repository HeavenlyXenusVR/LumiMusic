package com.lumisound.android.ui.screens.notifications

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
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Campaign
import androidx.compose.material.icons.filled.EmojiEvents
import androidx.compose.material.icons.filled.Group
import androidx.compose.material.icons.filled.NewReleases
import androidx.compose.material.icons.filled.Notifications
import androidx.compose.material.icons.filled.PersonAdd
import androidx.compose.material.icons.filled.Podcasts
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.dp
import com.lumisound.android.AppContainer
import com.lumisound.android.bridge.model.NotificationDto
import com.lumisound.android.ui.components.EmptyState
import com.lumisound.android.ui.components.Hairline
import com.lumisound.android.ui.components.LoadableSection
import com.lumisound.android.ui.components.friendlyError
import com.lumisound.android.ui.components.rememberLoadable
import com.lumisound.android.ui.screens.social.timeAgo
import com.lumisound.android.ui.screens.stats.DetailHeader
import com.lumisound.android.ui.theme.LocalLumiPalette
import kotlinx.coroutines.launch

/**
 * The account's in-app inbox: friend requests, achievement unlocks, new uploads from
 * followed channels, new podcast episodes and collaborator activity. The same rows
 * Lumisound's inbox reads, so reading one here marks it read there too.
 */
@Composable
fun NotificationsScreen(container: AppContainer, onBack: () -> Unit) {
    val scope = rememberCoroutineScope()
    val context = LocalContext.current
    val api = container.http.discovery
    val notifications = rememberLoadable(Unit, "notifications") { api.notifications(limit = 100) }
    // Marked read optimistically, so a tap changes the row at once rather than after a reload.
    var readLocally by remember { mutableStateOf(setOf<String>()) }

    Column(Modifier.fillMaxSize()) {
        DetailHeader("Inbox", "Notifications for this account", onBack, trailing = {
            val anyUnread = notifications.state.valueOrNull.orEmpty().any { it.isUnread && it.id !in readLocally }
            if (anyUnread) {
                TextButton(onClick = {
                    scope.launch {
                        try {
                            api.markAllNotificationsRead()
                            readLocally = notifications.state.valueOrNull.orEmpty().map { it.id }.toSet()
                        } catch (e: Exception) {
                            Toast.makeText(context, friendlyError(e), Toast.LENGTH_SHORT).show()
                        }
                    }
                }) { Text("Mark all read") }
            }
        })
        LoadableSection(notifications.state, onRetry = notifications::reload) { list ->
            if (list.isEmpty()) {
                EmptyState(Icons.Filled.Notifications, "All caught up", "Friend requests, badges and new releases will show up here.")
                return@LoadableSection
            }
            LazyColumn(Modifier.fillMaxSize(), contentPadding = PaddingValues(bottom = 28.dp)) {
                items(list, key = { it.id }) { item ->
                    val unread = item.isUnread && item.id !in readLocally
                    NotificationRow(item, unread) {
                        if (unread) {
                            readLocally = readLocally + item.id
                            scope.launch { runCatching { api.markNotificationRead(item.id) } }
                        }
                    }
                    Hairline(Modifier.padding(start = 70.dp))
                }
            }
        }
    }
}

private fun iconFor(type: String): ImageVector = when {
    type.startsWith("friend") -> Icons.Filled.PersonAdd
    type.startsWith("achievement") -> Icons.Filled.EmojiEvents
    type.startsWith("subscription") -> Icons.Filled.NewReleases
    type.startsWith("podcast") -> Icons.Filled.Podcasts
    type.startsWith("collab") || type.startsWith("playlist") -> Icons.Filled.Group
    type.startsWith("announcement") -> Icons.Filled.Campaign
    else -> Icons.Filled.Notifications
}

@Composable
private fun NotificationRow(item: NotificationDto, unread: Boolean, onClick: () -> Unit) {
    val palette = LocalLumiPalette.current
    Row(
        Modifier
            .fillMaxWidth()
            .background(if (unread) palette.accentWash.copy(alpha = 0.08f) else androidx.compose.ui.graphics.Color.Transparent)
            .clickable(onClick = onClick)
            .padding(horizontal = 18.dp, vertical = 12.dp),
        verticalAlignment = Alignment.Top,
        horizontalArrangement = Arrangement.spacedBy(14.dp),
    ) {
        Box(
            Modifier.size(38.dp).clip(RoundedCornerShape(11.dp)).background(palette.accentWash),
            contentAlignment = Alignment.Center,
        ) {
            Icon(iconFor(item.type), contentDescription = null, tint = palette.accent, modifier = Modifier.size(19.dp))
        }
        Column(Modifier.weight(1f)) {
            Text(item.title ?: "Notification", style = MaterialTheme.typography.titleSmall)
            item.body?.takeIf { it.isNotBlank() }?.let {
                Text(it, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
            }
        }
        Column(horizontalAlignment = Alignment.End) {
            Text(timeAgo(item.createdAt), style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
            if (unread) {
                Box(Modifier.padding(top = 6.dp).size(8.dp).clip(CircleShape).background(palette.accent))
            }
        }
    }
}

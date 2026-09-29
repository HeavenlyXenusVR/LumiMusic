package com.lumisound.android.ui.screens.settings

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.BarChart
import androidx.compose.material.icons.filled.BugReport
import androidx.compose.material.icons.filled.EmojiEvents
import androidx.compose.material.icons.filled.Notifications
import androidx.compose.material.icons.filled.Replay
import androidx.compose.material.icons.filled.Sync
import androidx.compose.material.icons.filled.CloudDownload
import androidx.compose.material.icons.filled.Dns
import androidx.compose.material.icons.filled.Download
import androidx.compose.material.icons.filled.Equalizer
import androidx.compose.material.icons.filled.Logout
import androidx.compose.material.icons.filled.Refresh
import androidx.compose.material3.Button
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.foundation.background
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import coil3.compose.AsyncImage
import com.lumisound.android.AppContainer
import com.lumisound.android.BuildConfig
import com.lumisound.android.bridge.AccountState
import com.lumisound.android.bridge.BridgeUrls
import com.lumisound.android.ui.Route
import com.lumisound.android.ui.components.Hairline
import com.lumisound.android.ui.components.LumiCard
import com.lumisound.android.ui.components.OneLine
import com.lumisound.android.ui.components.SettingsGroup
import com.lumisound.android.ui.components.SettingsRow
import com.lumisound.android.ui.theme.LocalLumiPalette
import kotlinx.coroutines.launch

@Composable
fun SettingsScreen(
    container: AppContainer,
    onOpenImport: () -> Unit,
    onOpen: (Route) -> Unit,
) {
    val scope = rememberCoroutineScope()
    val palette = LocalLumiPalette.current
    val account by container.account.state.collectAsStateWithLifecycle()
    val scanState by container.libraryScanner.state.collectAsStateWithLifecycle()
    val downloads by container.database.downloads().observePaths()
        .collectAsStateWithLifecycle(initialValue = emptyList())
    var bridgeUrl by remember { mutableStateOf(container.config.baseUrl) }
    var editingBridge by remember { mutableStateOf(false) }
    var note by remember { mutableStateOf<String?>(null) }

    val user = (account as? AccountState.SignedIn)?.user

    Column(
        Modifier
            .fillMaxSize()
            .verticalScroll(rememberScrollState())
            .padding(bottom = 28.dp)
    ) {
        Text(
            "Settings",
            style = MaterialTheme.typography.displaySmall,
            modifier = Modifier.padding(start = 20.dp, top = 12.dp, bottom = 14.dp),
        )

        // The account card leads, with the avatar the bridge already serves -- the same
        // image Lumisound shows for this account.
        LumiCard(Modifier.fillMaxWidth().padding(horizontal = 16.dp)) {
            Column {
                Row(
                    Modifier.fillMaxWidth().padding(16.dp),
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(14.dp),
                ) {
                    // The avatar the bridge already serves for this account -- the same
                    // image Lumisound shows. A missing one just leaves the accent wash.
                    Box(
                        Modifier
                            .size(54.dp)
                            .clip(CircleShape)
                            .background(palette.accentWash),
                        contentAlignment = Alignment.Center,
                    ) {
                        user?.id?.let { userId ->
                            AsyncImage(
                                model = BridgeUrls.avatar(container.config.baseUrl, userId),
                                contentDescription = null,
                                contentScale = ContentScale.Crop,
                                modifier = Modifier.size(54.dp).clip(CircleShape),
                            )
                        }
                    }
                    Column(Modifier.weight(1f)) {
                        OneLine(
                            user?.displayName?.takeIf { it.isNotBlank() } ?: user?.username ?: "Not signed in",
                            style = MaterialTheme.typography.titleMedium,
                        )
                        OneLine(
                            user?.username?.let { "@$it" } ?: "—",
                            style = MaterialTheme.typography.labelSmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                        )
                    }
                }
                Text(
                    "The same account Lumisound uses. Favorites, playlists, history and cloud tracks are shared between both apps.",
                    style = MaterialTheme.typography.labelSmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    modifier = Modifier.padding(horizontal = 16.dp),
                )
                Spacer(Modifier.height(14.dp))
                Row(
                    Modifier.fillMaxWidth().padding(horizontal = 16.dp, vertical = 4.dp),
                    horizontalArrangement = Arrangement.spacedBy(10.dp),
                ) {
                    Button(onClick = onOpenImport, modifier = Modifier.weight(1f)) {
                        Icon(Icons.Filled.CloudDownload, contentDescription = null, modifier = Modifier.size(18.dp))
                        Text("  Import")
                    }
                    TextButton(
                        onClick = {
                            scope.launch {
                                container.account.signOut()
                                container.appearance.clear()
                            }
                        },
                        modifier = Modifier.weight(1f),
                    ) {
                        Icon(Icons.Filled.Logout, contentDescription = null, modifier = Modifier.size(18.dp))
                        Text("  Sign out")
                    }
                }
                Spacer(Modifier.height(8.dp))
            }
        }

        Spacer(Modifier.height(22.dp))
        SettingsGroup("Your listening") {
            SettingsRow(
                icon = Icons.Filled.BarChart,
                title = "Stats",
                subtitle = "Totals, streaks, top artists and your year of listening",
                onClick = { onOpen(Route.Stats) },
            )
            Hairline()
            SettingsRow(
                icon = Icons.Filled.Replay,
                title = "Rewind",
                subtitle = "This month, this year and all time, wrapped",
                onClick = { onOpen(Route.Rewind) },
            )
            Hairline()
            SettingsRow(
                icon = Icons.Filled.EmojiEvents,
                title = "Achievements",
                subtitle = "Badges earned on every device",
                onClick = { onOpen(Route.Achievements) },
            )
            Hairline()
            SettingsRow(
                icon = Icons.Filled.Sync,
                title = "Scrobbling",
                subtitle = "Last.fm, Libre.fm and ListenBrainz",
                onClick = { onOpen(Route.Scrobbling) },
            )
            Hairline()
            SettingsRow(
                icon = Icons.Filled.Notifications,
                title = "Inbox",
                subtitle = "Friend requests, badges and new releases",
                onClick = { onOpen(Route.Notifications) },
            )
        }

        Spacer(Modifier.height(22.dp))
        SettingsGroup("Playback & storage") {
            SettingsRow(
                icon = Icons.Filled.Equalizer,
                title = "Equalizer",
                subtitle = "Your device's own bands and presets",
                onClick = { onOpen(Route.Equalizer) },
            )
            Hairline()
            SettingsRow(
                icon = Icons.Filled.Download,
                title = "Offline downloads",
                subtitle = if (downloads.isEmpty()) "Nothing saved yet" else "${downloads.size} tracks saved",
                onClick = { onOpen(Route.Downloads) },
            )
            Hairline()
            SettingsRow(
                icon = Icons.Filled.Refresh,
                title = "Rescan device library",
                subtitle = when {
                    scanState.running -> "Scanning…"
                    scanState.found > 0 -> "${scanState.found} tracks found"
                    scanState.lastRunAt != null -> "Last scan found nothing"
                    else -> "Not scanned yet"
                },
                onClick = { scope.launch { container.libraryScanner.scan() } },
            )
        }

        Spacer(Modifier.height(22.dp))
        SettingsGroup("Support") {
            SettingsRow(
                icon = Icons.Filled.BugReport,
                title = "Diagnostics & telemetry",
                subtitle = "Live state, logs, and report a problem",
                onClick = { onOpen(Route.Diagnostics) },
            )
            Hairline()
            SettingsRow(
                icon = Icons.Filled.Dns,
                title = "Bridge",
                subtitle = if (container.config.isOfficial) "Official bridge" else "Self-hosted",
                onClick = { editingBridge = !editingBridge },
            )
            if (editingBridge) {
                Column(Modifier.padding(horizontal = 16.dp, vertical = 8.dp)) {
                    OutlinedTextField(
                        value = bridgeUrl,
                        onValueChange = { bridgeUrl = it },
                        label = { Text("Bridge URL") },
                        singleLine = true,
                        shape = MaterialTheme.shapes.small,
                        modifier = Modifier.fillMaxWidth(),
                    )
                    Spacer(Modifier.height(8.dp))
                    Button(
                        onClick = {
                            container.config.baseUrl = bridgeUrl
                            bridgeUrl = container.config.baseUrl
                            editingBridge = false
                            note = "Saved. Sign out and back in if you changed servers."
                        },
                        modifier = Modifier.fillMaxWidth(),
                    ) { Text("Save bridge URL") }
                    Text(
                        "A self-hosted bridge never receives the official shared key.",
                        style = MaterialTheme.typography.labelSmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                        modifier = Modifier.padding(top = 6.dp),
                    )
                }
            }
        }

        note?.let {
            Spacer(Modifier.height(10.dp))
            Text(
                it,
                style = MaterialTheme.typography.labelSmall,
                color = palette.accent,
                modifier = Modifier.fillMaxWidth().padding(horizontal = 22.dp),
                textAlign = TextAlign.Center,
            )
        }

        Spacer(Modifier.height(26.dp))
        Text(
            "LumiMusic ${BuildConfig.VERSION_NAME} (${BuildConfig.VERSION_CODE})",
            style = MaterialTheme.typography.labelSmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            modifier = Modifier.fillMaxWidth(),
            textAlign = TextAlign.Center,
        )
    }
}

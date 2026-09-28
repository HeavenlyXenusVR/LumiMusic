package com.lumisound.android.ui.screens.settings

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Button
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.lumisound.android.AppContainer
import com.lumisound.android.BuildConfig
import com.lumisound.android.bridge.AccountState
import com.lumisound.android.ui.SettingsDestination
import kotlinx.coroutines.launch

@Composable
fun SettingsScreen(
    container: AppContainer,
    onOpenImport: () -> Unit,
    onOpen: (SettingsDestination) -> Unit,
) {
    val scope = rememberCoroutineScope()
    val account by container.account.state.collectAsStateWithLifecycle()
    var bridgeUrl by remember { mutableStateOf(container.config.baseUrl) }
    var savedNote by remember { mutableStateOf<String?>(null) }

    Column(
        Modifier
            .fillMaxSize()
            .verticalScroll(rememberScrollState())
            .padding(16.dp)
    ) {
        Text("Account", style = MaterialTheme.typography.titleMedium)
        Spacer(Modifier.height(6.dp))
        val user = (account as? AccountState.SignedIn)?.user
        Text(
            user?.let { "${it.displayName ?: it.username} (@${it.username})" } ?: "Not signed in",
            style = MaterialTheme.typography.bodyMedium,
        )
        Text(
            "This is the same account Lumisound uses. Favorites, playlists, history and " +
                "cloud tracks are shared between both apps.",
            style = MaterialTheme.typography.labelSmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
        )
        Spacer(Modifier.height(12.dp))
        Button(onClick = onOpenImport, modifier = Modifier.fillMaxWidth()) {
            Text("Import cloud data")
        }
        Spacer(Modifier.height(8.dp))
        OutlinedButton(
            onClick = { scope.launch { container.account.signOut() } },
            modifier = Modifier.fillMaxWidth(),
        ) {
            Text("Sign out")
        }

        Spacer(Modifier.height(24.dp))
        HorizontalDivider()
        Spacer(Modifier.height(16.dp))

        Text("Playback & storage", style = MaterialTheme.typography.titleMedium)
        Spacer(Modifier.height(8.dp))
        OutlinedButton(onClick = { onOpen(SettingsDestination.Equalizer) }, modifier = Modifier.fillMaxWidth()) {
            Text("Equalizer")
        }
        Spacer(Modifier.height(8.dp))
        OutlinedButton(onClick = { onOpen(SettingsDestination.Downloads) }, modifier = Modifier.fillMaxWidth()) {
            Text("Offline downloads")
        }
        Spacer(Modifier.height(8.dp))
        OutlinedButton(onClick = { onOpen(SettingsDestination.Diagnostics) }, modifier = Modifier.fillMaxWidth()) {
            Text("Diagnostics & telemetry")
        }
        Spacer(Modifier.height(8.dp))
        OutlinedButton(
            onClick = { scope.launch { container.libraryScanner.scan() } },
            modifier = Modifier.fillMaxWidth(),
        ) {
            Text("Rescan device library")
        }

        Spacer(Modifier.height(24.dp))
        HorizontalDivider()
        Spacer(Modifier.height(16.dp))

        Text("Bridge", style = MaterialTheme.typography.titleMedium)
        Spacer(Modifier.height(6.dp))
        Text(
            if (container.config.isOfficial) "Pointed at the official bridge."
            else "Pointed at a self-hosted bridge. The official shared key is not sent to it.",
            style = MaterialTheme.typography.labelSmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
        )
        Spacer(Modifier.height(10.dp))
        OutlinedTextField(
            value = bridgeUrl,
            onValueChange = { bridgeUrl = it },
            label = { Text("Bridge URL") },
            singleLine = true,
            modifier = Modifier.fillMaxWidth(),
        )
        Spacer(Modifier.height(8.dp))
        Button(
            onClick = {
                container.config.baseUrl = bridgeUrl
                bridgeUrl = container.config.baseUrl
                savedNote = "Saved. Sign out and back in if you changed servers."
            },
            modifier = Modifier.fillMaxWidth(),
        ) {
            Text("Save bridge URL")
        }
        savedNote?.let {
            Spacer(Modifier.height(6.dp))
            Text(it, style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.primary)
        }

        Spacer(Modifier.height(24.dp))
        HorizontalDivider()
        Spacer(Modifier.height(16.dp))
        Text(
            "LumiMusic ${BuildConfig.VERSION_NAME}",
            style = MaterialTheme.typography.labelSmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            modifier = Modifier.clickable { }
        )
    }
}

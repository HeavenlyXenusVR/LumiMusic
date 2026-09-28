package com.lumisound.android.ui

import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.CloudQueue
import androidx.compose.material.icons.filled.Favorite
import androidx.compose.material.icons.filled.PlaylistPlay
import androidx.compose.material.icons.filled.Settings
import androidx.compose.material3.Icon
import androidx.compose.material3.NavigationBar
import androidx.compose.material3.NavigationBarItem
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.lumisound.android.AppContainer
import com.lumisound.android.bridge.AccountState
import com.lumisound.android.ui.screens.cloud.CloudLibraryScreen
import com.lumisound.android.ui.screens.importer.ImportScreen
import com.lumisound.android.ui.screens.library.FavoritesScreen
import com.lumisound.android.ui.screens.library.PlaylistsScreen
import com.lumisound.android.ui.screens.nowplaying.MiniPlayer
import com.lumisound.android.ui.screens.nowplaying.NowPlayingSheet
import com.lumisound.android.ui.screens.settings.SettingsScreen
import com.lumisound.android.ui.screens.signin.SignInScreen

private enum class Tab(val label: String, val icon: ImageVector) {
    Cloud("Cloud", Icons.Filled.CloudQueue),
    Favorites("Favorites", Icons.Filled.Favorite),
    Playlists("Playlists", Icons.Filled.PlaylistPlay),
    Settings("Settings", Icons.Filled.Settings),
}

/**
 * Sign-in gates everything: this app has no local-only mode yet, because the
 * first milestone is specifically "the account and its cloud library work here",
 * and pretending otherwise would mean a home screen with nothing in it.
 */
@Composable
fun LumiMusicRoot(container: AppContainer) {
    val accountState by container.account.state.collectAsStateWithLifecycle()

    when (accountState) {
        AccountState.Unknown -> SignInScreen(container, checkingSession = true)
        AccountState.SignedOut -> SignInScreen(container, checkingSession = false)
        is AccountState.SignedIn -> SignedInShell(container)
    }
}

@Composable
private fun SignedInShell(container: AppContainer) {
    var tab by remember { mutableStateOf(Tab.Cloud) }
    var showNowPlaying by remember { mutableStateOf(false) }
    var showImport by remember { mutableStateOf(false) }
    val playback by container.player.state.collectAsStateWithLifecycle()

    Scaffold(
        bottomBar = {
            Column {
                if (playback.hasQueue) {
                    MiniPlayer(
                        state = playback,
                        onToggle = container.player::togglePlayPause,
                        onNext = { container.player.next() },
                        onExpand = { showNowPlaying = true },
                    )
                }
                NavigationBar {
                    Tab.entries.forEach { entry ->
                        NavigationBarItem(
                            selected = tab == entry,
                            onClick = { tab = entry },
                            icon = { Icon(entry.icon, contentDescription = entry.label) },
                            label = { Text(entry.label) },
                        )
                    }
                }
            }
        }
    ) { padding ->
        Column(Modifier.fillMaxSize().padding(padding)) {
            when (tab) {
                Tab.Cloud -> CloudLibraryScreen(container, onOpenImport = { showImport = true })
                Tab.Favorites -> FavoritesScreen(container)
                Tab.Playlists -> PlaylistsScreen(container)
                Tab.Settings -> SettingsScreen(container, onOpenImport = { showImport = true })
            }
        }
    }

    if (showNowPlaying) {
        NowPlayingSheet(container, onDismiss = { showNowPlaying = false })
    }
    if (showImport) {
        ImportScreen(container, onDismiss = { showImport = false })
    }
}

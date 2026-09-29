package com.lumisound.android.ui

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Home
import androidx.compose.material.icons.filled.LibraryMusic
import androidx.compose.material.icons.filled.People
import androidx.compose.material.icons.filled.Search
import androidx.compose.material.icons.filled.Settings
import androidx.compose.material3.Icon
import androidx.compose.material3.NavigationBar
import androidx.compose.material3.NavigationBarItem
import androidx.compose.material3.NavigationBarItemDefaults
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.activity.compose.BackHandler
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.material3.MaterialTheme
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.lumisound.android.AppContainer
import com.lumisound.android.bridge.AccountState
import com.lumisound.android.ui.screens.cloud.CloudLibraryScreen
import com.lumisound.android.ui.screens.device.DeviceLibraryScreen
import com.lumisound.android.ui.screens.diagnostics.DiagnosticsScreen
import com.lumisound.android.ui.screens.downloads.DownloadsScreen
import com.lumisound.android.ui.screens.eq.EqualizerScreen
import com.lumisound.android.ui.screens.importer.ImportScreen
import com.lumisound.android.ui.screens.library.AddToPlaylistDialog
import com.lumisound.android.ui.screens.home.HomeScreen
import com.lumisound.android.ui.screens.library.FavoritesScreen
import com.lumisound.android.ui.screens.library.LibraryHub
import com.lumisound.android.ui.screens.library.LibrarySection
import com.lumisound.android.ui.screens.notifications.NotificationsScreen
import com.lumisound.android.ui.screens.podcasts.PodcastDetailScreen
import com.lumisound.android.ui.screens.podcasts.PodcastsScreen
import com.lumisound.android.ui.screens.search.SearchScreen
import com.lumisound.android.ui.screens.settings.ScrobblingScreen
import com.lumisound.android.ui.screens.social.ProfileScreen
import com.lumisound.android.ui.screens.social.SocialScreen
import com.lumisound.android.ui.screens.stats.AchievementsScreen
import com.lumisound.android.ui.screens.stats.RewindScreen
import com.lumisound.android.ui.screens.stats.StatsScreen
import com.lumisound.android.ui.screens.library.PlaylistsScreen
import com.lumisound.android.ui.screens.nowplaying.MiniPlayer
import com.lumisound.android.ui.screens.nowplaying.NowPlayingSheet
import com.lumisound.android.ui.screens.queue.QueueSheet
import com.lumisound.android.ui.screens.settings.SettingsScreen
import com.lumisound.android.ui.screens.signin.SignInScreen
import com.lumisound.android.ui.theme.LocalLumiPalette
import kotlinx.coroutines.launch

private enum class Tab(val label: String, val icon: ImageVector) {
    Home("Home", Icons.Filled.Home),
    Search("Search", Icons.Filled.Search),
    Library("Library", Icons.Filled.LibraryMusic),
    Friends("Friends", Icons.Filled.People),
    Settings("Settings", Icons.Filled.Settings),
}

/** A track waiting to be filed into a playlist. */
private data class PendingPlaylistAdd(
    val title: String,
    val artist: String?,
    val album: String?,
    val songId: String,
    val durationSeconds: Int,
)

/**
 * Sign-in gates everything: this app has no local-only mode yet, because nearly every
 * screen -- the dashboard, search, friends, the cloud library -- is the account's, and
 * pretending otherwise would mean a home screen with nothing in it.
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
    val palette = LocalLumiPalette.current
    val scope = androidx.compose.runtime.rememberCoroutineScope()
    var tab by rememberSaveable { mutableStateOf(Tab.Home) }
    var librarySection by rememberSaveable { mutableStateOf(LibrarySection.Cloud) }
    // Screens pushed over the current tab; back pops one, and switching tabs clears them.
    var stack by remember { mutableStateOf(listOf<Route>()) }
    val push: (Route) -> Unit = { stack = stack + it }
    val pop: () -> Unit = { stack = stack.dropLast(1) }
    BackHandler(enabled = stack.isNotEmpty(), onBack = pop)
    BackHandler(enabled = stack.isEmpty() && tab != Tab.Home) { tab = Tab.Home }
    var showNowPlaying by remember { mutableStateOf(false) }
    var showQueue by remember { mutableStateOf(false) }
    var showImport by remember { mutableStateOf(false) }
    var pendingAdd by remember { mutableStateOf<PendingPlaylistAdd?>(null) }
    val rawPlayback by container.player.state.collectAsStateWithLifecycle()
    val favoriteIds by container.database.favorites().observeIds()
        .collectAsStateWithLifecycle(initialValue = emptyList())
    val playback = rawPlayback.copy(isFavorite = rawPlayback.serverPath in favoriteIds)

    Scaffold(
        // The page gradient lives on the scaffold so every screen shares one background
        // rather than each drawing its own flat panel.
        containerColor = androidx.compose.ui.graphics.Color.Transparent,
        modifier = Modifier.fillMaxSize().background(palette.pageBrush),
        bottomBar = {
            Column {
                if (playback.hasQueue) {
                    MiniPlayer(
                        state = playback,
                        onToggle = container.player::togglePlayPause,
                        onNext = { container.player.next() },
                        onExpand = { showNowPlaying = true },
                        onFavorite = {
                            val path = playback.serverPath ?: return@MiniPlayer
                            scope.launch {
                                container.libraryRepository.toggleFavorite(
                                    path, playback.title, playback.artist, null,
                                )
                            }
                        },
                    )
                }
                NavigationBar(
                    containerColor = palette.elevatedSurface,
                    tonalElevation = 0.dp,
                ) {
                    Tab.entries.forEach { entry ->
                        NavigationBarItem(
                            selected = tab == entry && stack.isEmpty(),
                            onClick = { tab = entry; stack = emptyList() },
                            icon = { Icon(entry.icon, contentDescription = entry.label) },
                            label = { Text(entry.label, style = MaterialTheme.typography.labelSmall) },
                            colors = NavigationBarItemDefaults.colors(
                                selectedIconColor = palette.accent,
                                selectedTextColor = palette.accent,
                                indicatorColor = palette.accentWash,
                            ),
                        )
                    }
                }
            }
        }
    ) { padding ->
        Column(Modifier.fillMaxSize().padding(padding)) {
            when (val route = stack.lastOrNull()) {
                Route.Equalizer -> EqualizerScreen(container)
                Route.Downloads -> DownloadsScreen(container)
                Route.Diagnostics -> DiagnosticsScreen(container)
                Route.Stats -> StatsScreen(container, onBack = pop)
                Route.Rewind -> RewindScreen(container, onBack = pop)
                Route.Achievements -> AchievementsScreen(container, onBack = pop)
                Route.Notifications -> NotificationsScreen(container, onBack = pop)
                Route.Scrobbling -> ScrobblingScreen(container, onBack = pop)
                Route.Podcasts -> PodcastsScreen(container, onOpen = push, onBack = pop)
                is Route.Podcast -> PodcastDetailScreen(container, route.feedUrl, route.title, route.artworkUrl, onBack = pop)
                is Route.Profile -> ProfileScreen(container, route.userId, route.name, onBack = pop)
                null -> when (tab) {
                    Tab.Home -> HomeScreen(
                        container = container,
                        onOpen = push,
                        onOpenFriends = { tab = Tab.Friends },
                    )
                    Tab.Search -> SearchScreen(container)
                    Tab.Library -> LibraryHub(librarySection, onSectionChange = { librarySection = it }) { section ->
                        when (section) {
                            LibrarySection.Cloud -> CloudLibraryScreen(
                                container = container,
                                onOpenImport = { showImport = true },
                                onAddToPlaylist = { title, artist, album, songId, duration ->
                                    pendingAdd = PendingPlaylistAdd(title, artist, album, songId, duration)
                                },
                            )
                            LibrarySection.Device -> DeviceLibraryScreen(container)
                            LibrarySection.Favorites -> FavoritesScreen(container)
                            LibrarySection.Playlists -> PlaylistsScreen(container)
                            LibrarySection.Podcasts -> PodcastsScreen(container, onOpen = push, onBack = null)
                        }
                    }
                    Tab.Friends -> SocialScreen(container, onOpen = push)
                    Tab.Settings -> SettingsScreen(
                        container = container,
                        onOpenImport = { showImport = true },
                        onOpen = push,
                    )
                }
            }
        }
    }

    if (showNowPlaying) {
        NowPlayingSheet(
            container = container,
            onOpenQueue = { showNowPlaying = false; showQueue = true },
            onDismiss = { showNowPlaying = false },
        )
    }
    if (showQueue) {
        QueueSheet(container, onDismiss = { showQueue = false })
    }
    if (showImport) {
        ImportScreen(container, onDismiss = { showImport = false })
    }
    pendingAdd?.let { pending ->
        AddToPlaylistDialog(
            container = container,
            title = pending.title,
            artist = pending.artist,
            album = pending.album,
            songId = pending.songId,
            durationSeconds = pending.durationSeconds,
            onDismiss = { pendingAdd = null },
        )
    }
}

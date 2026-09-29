package com.lumisound.android.ui.screens.library

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.unit.dp
import com.lumisound.android.ui.theme.LocalLumiPalette

/** The parts of the library, each an existing screen, reached from one tab. */
enum class LibrarySection(val label: String) {
    Cloud("Cloud"),
    Device("Device"),
    Favorites("Favorites"),
    Playlists("Playlists"),
    Podcasts("Podcasts"),
}

/**
 * Everything owned, under one tab: the cloud library, the device's own files, favorites,
 * playlists and followed podcasts. A quiet text switcher rather than a second row of chips,
 * because several of the screens underneath already carry chips of their own.
 */
@Composable
fun LibraryHub(
    section: LibrarySection,
    onSectionChange: (LibrarySection) -> Unit,
    content: @Composable (LibrarySection) -> Unit,
) {
    val palette = LocalLumiPalette.current
    Column(Modifier.fillMaxSize()) {
        Row(
            Modifier
                .fillMaxWidth()
                .horizontalScroll(rememberScrollState())
                .padding(start = 12.dp, end = 12.dp, top = 6.dp),
            horizontalArrangement = Arrangement.spacedBy(4.dp),
        ) {
            LibrarySection.entries.forEach { entry ->
                val selected = entry == section
                Column(
                    Modifier
                        .clip(CircleShape)
                        .clickable { onSectionChange(entry) }
                        .padding(horizontal = 10.dp, vertical = 6.dp),
                    horizontalAlignment = Alignment.CenterHorizontally,
                ) {
                    Text(
                        entry.label,
                        style = if (selected) MaterialTheme.typography.titleSmall else MaterialTheme.typography.labelLarge,
                        color = if (selected) MaterialTheme.colorScheme.onSurface else MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                    Spacer(Modifier.height(4.dp))
                    Box(
                        Modifier
                            .width(18.dp)
                            .height(3.dp)
                            .clip(CircleShape)
                            .background(if (selected) palette.accent else palette.accent.copy(alpha = 0f))
                    )
                }
            }
        }
        Box(Modifier.weight(1f)) { content(section) }
    }
}

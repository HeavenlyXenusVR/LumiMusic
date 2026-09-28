package com.lumisound.android.ui.components

import androidx.compose.animation.animateColorAsState
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.CheckCircle
import androidx.compose.material.icons.filled.Favorite
import androidx.compose.material.icons.filled.GraphicEq
import androidx.compose.material.icons.filled.Lock
import androidx.compose.material.icons.filled.MoreVert
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.unit.dp
import com.lumisound.android.ui.theme.LocalLumiPalette

/**
 * The one track row every list uses.
 *
 * Deliberately one implementation rather than one per screen: the cloud library, the
 * device library, favorites and playlists all show the same thing, and the first build's
 * rows drifted apart immediately because each screen drew its own.
 */
@Composable
fun TrackRow(
    title: String,
    subtitle: String,
    artworkModel: Any?,
    fallbackKey: String,
    modifier: Modifier = Modifier,
    duration: String? = null,
    isPlaying: Boolean = false,
    isFavorite: Boolean = false,
    isDownloaded: Boolean = false,
    isLocked: Boolean = false,
    onClick: () -> Unit = {},
    onMenu: (() -> Unit)? = null,
) {
    val palette = LocalLumiPalette.current
    val background by animateColorAsState(
        if (isPlaying) palette.accentWash else Color.Transparent,
        label = "rowBackground",
    )

    Row(
        modifier
            .fillMaxWidth()
            .background(background)
            .clickable(onClick = onClick)
            .padding(start = 16.dp, end = if (onMenu == null) 16.dp else 4.dp, top = 8.dp, bottom = 8.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(13.dp),
    ) {
        Box {
            Artwork(model = artworkModel, fallbackKey = fallbackKey, size = 50.dp)
            if (isPlaying) {
                // A playing row is marked on the artwork itself; a coloured title alone was
                // too easy to miss while scrolling.
                Box(
                    Modifier
                        .size(50.dp)
                        .background(Color.Black.copy(alpha = 0.45f), MaterialTheme.shapes.extraSmall),
                    contentAlignment = Alignment.Center,
                ) {
                    Icon(
                        Icons.Filled.GraphicEq,
                        contentDescription = "Now playing",
                        tint = palette.accent,
                        modifier = Modifier.size(22.dp),
                    )
                }
            }
        }

        Column(Modifier.weight(1f)) {
            OneLine(
                title,
                style = MaterialTheme.typography.bodyLarge,
                color = if (isPlaying) palette.accent else MaterialTheme.colorScheme.onSurface,
            )
            Spacer(Modifier.height(2.dp))
            Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                if (isFavorite) {
                    Icon(
                        Icons.Filled.Favorite,
                        contentDescription = "Favorite",
                        tint = palette.accent,
                        modifier = Modifier.size(12.dp),
                    )
                }
                if (isDownloaded) {
                    Icon(
                        Icons.Filled.CheckCircle,
                        contentDescription = "Offline",
                        tint = MaterialTheme.colorScheme.onSurfaceVariant,
                        modifier = Modifier.size(12.dp),
                    )
                }
                if (isLocked) {
                    Icon(
                        Icons.Filled.Lock,
                        contentDescription = "Lumisound-locked",
                        tint = MaterialTheme.colorScheme.onSurfaceVariant,
                        modifier = Modifier.size(12.dp),
                    )
                }
                OneLine(
                    subtitle,
                    style = MaterialTheme.typography.labelMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    modifier = Modifier.weight(1f, fill = false),
                )
            }
        }

        duration?.let {
            Text(
                it,
                style = MaterialTheme.typography.labelSmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                modifier = Modifier.width(40.dp),
            )
        }

        onMenu?.let {
            IconButton(onClick = it) {
                Icon(
                    Icons.Filled.MoreVert,
                    contentDescription = "More",
                    tint = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }
        }
    }
}

/** The square card used in the home shelves. */
@Composable
fun ShelfCard(
    title: String,
    subtitle: String,
    artworkModel: Any?,
    fallbackKey: String,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
) {
    Column(
        modifier
            .width(132.dp)
            .clickable(onClick = onClick)
            .padding(6.dp)
    ) {
        Artwork(model = artworkModel, fallbackKey = fallbackKey, size = 120.dp, corner = 14.dp)
        Spacer(Modifier.height(8.dp))
        OneLine(title, style = MaterialTheme.typography.labelLarge)
        OneLine(
            subtitle,
            style = MaterialTheme.typography.labelSmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
        )
    }
}

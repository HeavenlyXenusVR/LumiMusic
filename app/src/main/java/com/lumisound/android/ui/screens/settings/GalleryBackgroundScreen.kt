package com.lumisound.android.ui.screens.settings

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyListScope
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.AutoAwesome
import androidx.compose.material.icons.filled.BlurOn
import androidx.compose.material.icons.filled.Collections
import androidx.compose.material.icons.filled.Opacity
import androidx.compose.material.icons.filled.PhoneIphone
import androidx.compose.material.icons.filled.Sync
import androidx.compose.material.icons.filled.Timer
import androidx.compose.material.icons.filled.Wallpaper
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Slider
import androidx.compose.material3.SliderDefaults
import androidx.compose.material3.Switch
import androidx.compose.material3.SwitchDefaults
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.lumisound.android.AppContainer
import com.lumisound.android.gallery.GallerySettings
import com.lumisound.android.gallery.GalleryState
import com.lumisound.android.gallery.GalleryTransition
import com.lumisound.android.ui.aura.AuraBase
import com.lumisound.android.ui.components.ChipRow
import com.lumisound.android.ui.components.EmptyState
import com.lumisound.android.ui.components.GlassButton
import com.lumisound.android.ui.components.GlassPanel
import com.lumisound.android.ui.components.IconSectionHeader
import com.lumisound.android.ui.components.NavChip
import com.lumisound.android.ui.components.ScreenTitle
import com.lumisound.android.ui.components.SettingsGroup
import com.lumisound.android.ui.components.SettingsRow
import com.lumisound.android.ui.gallery.GalleryBackdrop
import com.lumisound.android.ui.gallery.GalleryImage
import com.lumisound.android.ui.theme.LocalLumiPalette
import kotlinx.coroutines.launch

data class GalleryCallbacks(
    val onBack: (() -> Unit)? = null,
    val onSync: () -> Unit = {},
    val onFollow: (Boolean) -> Unit = {},
    val onChange: ((GallerySettings) -> GallerySettings) -> Unit = {},
)

@Composable
fun GalleryBackgroundScreen(container: AppContainer, onBack: () -> Unit) {
    val scope = rememberCoroutineScope()
    val state by container.gallery.state.collectAsStateWithLifecycle()
    GalleryBackgroundContent(
        state = state,
        callbacks = GalleryCallbacks(
            onBack = onBack,
            onSync = { scope.launch { container.gallery.sync() } },
            onFollow = container.gallery::setFollowLumisound,
            onChange = container.gallery::updateSettings,
        ),
    )
}

/**
 * Gallery Background: the photos backed up by Lumisound on iPhone, shown behind every
 * screen. The photos can only be managed there (see `GalleryApi`); this screen previews
 * them and sets how they look on this phone.
 */
@Composable
fun GalleryBackgroundContent(state: GalleryState, callbacks: GalleryCallbacks, nowMs: Long = System.currentTimeMillis()) {
    val palette = LocalLumiPalette.current
    val settings = state.settings
    LazyColumn(
        contentPadding = PaddingValues(bottom = 32.dp),
        verticalArrangement = Arrangement.spacedBy(8.dp),
        modifier = Modifier.fillMaxSize(),
    ) {
        full {
            val synced = state.lastSyncedAt?.let { syncedLabel(it, nowMs) }
            ScreenTitle(
                "Gallery background",
                subtitle = listOfNotNull("From Lumisound on iPhone", synced).joinToString(" · "),
                eyebrow = "Appearance",
                onBack = callbacks.onBack,
            )
        }
        full { BackgroundPreview(state) }
        full {
            Column(Modifier.padding(top = 16.dp), verticalArrangement = Arrangement.spacedBy(16.dp)) {
                SettingsGroup("Sync") {
                    SettingsRow(
                        Icons.Filled.Wallpaper,
                        "Show behind every screen",
                        if (state.photos.isEmpty()) "Waiting for photos from your iPhone" else "${state.photos.size} photos",
                        trailing = {
                            Switch(
                                checked = settings.enabled,
                                onCheckedChange = { on -> callbacks.onChange { it.copy(enabled = on) } },
                                colors = SwitchDefaults.colors(checkedTrackColor = palette.accent),
                            )
                        },
                    )
                    SettingsRow(
                        Icons.Filled.PhoneIphone,
                        "Match my iPhone",
                        if (state.followLumisound) "On/off, opacity, blur, timing and transition follow Lumisound"
                        else "This phone keeps its own look",
                        trailing = {
                            Switch(
                                checked = state.followLumisound,
                                onCheckedChange = callbacks.onFollow,
                                colors = SwitchDefaults.colors(checkedTrackColor = palette.accent),
                            )
                        },
                    )
                }
            }
        }
        full {
            Column {
                IconSectionHeader(Icons.Filled.AutoAwesome, "Look")
                GlassPanel {
                    LabeledSlider(Icons.Filled.Opacity, "Opacity", "${Math.round(settings.opacity * 100)}%") {
                        Slider(
                            value = settings.opacity,
                            valueRange = GallerySettings.MIN_OPACITY..1f,
                            onValueChange = { v -> callbacks.onChange { it.copy(opacity = v) } },
                            colors = SliderDefaults.colors(thumbColor = Color.White, activeTrackColor = palette.accent),
                        )
                    }
                    LabeledSlider(Icons.Filled.BlurOn, "Blur", "${Math.round(settings.blurRadius)}") {
                        Slider(
                            value = settings.blurRadius,
                            valueRange = 0f..GallerySettings.MAX_BLUR,
                            onValueChange = { v -> callbacks.onChange { it.copy(blurRadius = v) } },
                            colors = SliderDefaults.colors(thumbColor = Color.White, activeTrackColor = palette.accent),
                        )
                    }
                    Row(
                        Modifier.fillMaxWidth().padding(top = 4.dp),
                        verticalAlignment = Alignment.CenterVertically,
                    ) {
                        Column(Modifier.weight(1f)) {
                            Text("Ken Burns motion", style = MaterialTheme.typography.titleSmall)
                            Text(
                                "A slow drift and zoom while each photo is up",
                                style = MaterialTheme.typography.labelSmall,
                                color = MaterialTheme.colorScheme.onSurfaceVariant,
                            )
                        }
                        Switch(
                            checked = settings.kenBurns,
                            onCheckedChange = { on -> callbacks.onChange { it.copy(kenBurns = on) } },
                            colors = SwitchDefaults.colors(checkedTrackColor = palette.accent),
                        )
                    }
                }
            }
        }
        full {
            Column {
                IconSectionHeader(Icons.Filled.Timer, "Next photo every")
                ChipRow(
                    GallerySettings.INTERVAL_PRESETS.map { seconds ->
                        NavChip(intervalLabel(seconds), Icons.Filled.Timer, selected = settings.intervalSeconds == seconds) {
                            callbacks.onChange { it.copy(intervalSeconds = seconds) }
                        }
                    }
                )
                IconSectionHeader(Icons.Filled.AutoAwesome, "Transition")
                ChipRow(
                    GalleryTransition.entries.map { transition ->
                        NavChip(transition.label, Icons.Filled.AutoAwesome, selected = settings.transition == transition) {
                            callbacks.onChange { it.copy(transition = transition) }
                        }
                    }
                )
            }
        }
        full {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Box(Modifier.weight(1f)) { IconSectionHeader(Icons.Filled.Collections, "Photos") }
                GlassButton(
                    if (state.syncing) "Syncing…" else "Sync now",
                    Icons.Filled.Sync,
                    onClick = callbacks.onSync,
                    modifier = Modifier.padding(end = 16.dp),
                )
            }
        }
        state.error?.let { error ->
            full {
                Text(
                    "Last sync failed: $error",
                    style = MaterialTheme.typography.labelMedium,
                    color = MaterialTheme.colorScheme.error,
                    modifier = Modifier.padding(horizontal = 20.dp),
                )
            }
        }
        if (state.photos.isEmpty()) {
            full {
                EmptyState(
                    Icons.Filled.Collections,
                    "No photos yet",
                    "In Lumisound on your iPhone, open Settings › Background and add photos. They back up to your account and show up here on their own.",
                )
            }
        } else {
            // Rows of three, each one lazy item, so a large gallery only loads what is on screen.
            items(state.photos.chunked(3), key = { row -> row.first().id }) { row ->
                Row(Modifier.padding(horizontal = 16.dp), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    row.forEach { photo ->
                        GalleryImage(
                            photo,
                            sizePx = 360,
                            modifier = Modifier
                                .weight(1f)
                                .aspectRatio(0.75f)
                                .clip(RoundedCornerShape(14.dp))
                                .background(Color.White.copy(alpha = 0.06f)),
                        )
                    }
                    repeat(3 - row.size) { Spacer(Modifier.weight(1f)) }
                }
            }
            full {
                Text(
                    "Add or remove photos in Lumisound on your iPhone; this list follows it.",
                    style = MaterialTheme.typography.labelSmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    modifier = Modifier.padding(horizontal = 20.dp, vertical = 8.dp),
                )
            }
        }
    }
}

/** The live background in a phone-shaped window, so every change shows as it is made. */
@Composable
private fun BackgroundPreview(state: GalleryState) {
    val palette = LocalLumiPalette.current
    Box(Modifier.fillMaxWidth(), contentAlignment = Alignment.Center) {
        Box(
            Modifier
                .fillMaxWidth(0.52f)
                .aspectRatio(9f / 16f)
                .clip(RoundedCornerShape(28.dp))
                .background(AuraBase),
        ) {
            GalleryBackdrop(state.photos, state.settings.copy(enabled = true))
            Box(
                Modifier.fillMaxSize().background(
                    Brush.verticalGradient(listOf(palette.accent.copy(alpha = 0.18f), Color.Transparent, AuraBase.copy(alpha = 0.7f)))
                )
            )
            Column(Modifier.align(Alignment.BottomStart).padding(14.dp)) {
                Text("Now playing", style = MaterialTheme.typography.labelSmall, color = Color.White.copy(alpha = 0.7f))
                Text("Your background", style = MaterialTheme.typography.titleSmall, color = Color.White)
                Spacer(Modifier.height(8.dp))
                Box(Modifier.fillMaxWidth().height(4.dp).clip(RoundedCornerShape(2.dp)).background(Color.White.copy(alpha = 0.25f))) {
                    Box(Modifier.fillMaxWidth(0.4f).height(4.dp).background(palette.accent))
                }
            }
            if (!state.settings.enabled) {
                Box(Modifier.fillMaxSize().background(AuraBase.copy(alpha = 0.55f)), contentAlignment = Alignment.Center) {
                    Text("Off", style = MaterialTheme.typography.titleMedium, color = Color.White)
                }
            }
        }
    }
}

@Composable
private fun LabeledSlider(icon: ImageVector, label: String, value: String, slider: @Composable () -> Unit) {
    Column {
        Row(verticalAlignment = Alignment.CenterVertically) {
            Icon(icon, contentDescription = null, modifier = Modifier.padding(end = 8.dp))
            Text(label, style = MaterialTheme.typography.titleSmall, modifier = Modifier.weight(1f))
            Text(value, style = MaterialTheme.typography.labelLarge, color = MaterialTheme.colorScheme.onSurfaceVariant)
        }
        slider()
    }
}

private fun LazyListScope.full(content: @Composable () -> Unit) {
    item { content() }
}

/** "synced just now", "synced 5 min ago", "synced 3 h ago", "synced 2 days ago". */
fun syncedLabel(syncedAtMs: Long, nowMs: Long): String {
    val minutes = ((nowMs - syncedAtMs) / 60_000L).coerceAtLeast(0L)
    return when {
        minutes < 1 -> "synced just now"
        minutes < 60 -> "synced $minutes min ago"
        minutes < 60 * 24 -> "synced ${minutes / 60} h ago"
        else -> "synced ${minutes / (60 * 24)} days ago"
    }
}

fun intervalLabel(seconds: Int): String = when {
    seconds < 60 -> "${seconds}s"
    seconds % 60 == 0 -> "${seconds / 60} min"
    else -> "${seconds / 60}m ${seconds % 60}s"
}

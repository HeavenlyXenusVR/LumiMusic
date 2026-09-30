package com.lumisound.android.ui.screens.downloads

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Folder
import androidx.compose.material.icons.filled.Sync
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Switch
import androidx.compose.material3.SwitchDefaults
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.unit.dp
import com.lumisound.android.download.DownloadManager
import com.lumisound.android.download.OfflinePrefs
import com.lumisound.android.ui.components.GlassButton
import com.lumisound.android.ui.components.GlassPanel
import com.lumisound.android.ui.components.Hairline
import com.lumisound.android.ui.theme.LocalLumiPalette

data class LibraryDownloadCallbacks(
    val onWholeLibrary: (Boolean) -> Unit = {},
    val onWifiOnly: (Boolean) -> Unit = {},
    val onSyncNow: () -> Unit = {},
    val onChooseFolder: () -> Unit = {},
    val onUsePhoneFolder: () -> Unit = {},
)

/**
 * The whole-library switch at the top of Offline: keep every cloud track on the phone,
 * where, and how far along it is.
 */
@Composable
fun LibraryDownloadPanel(
    prefs: OfflinePrefs,
    sync: DownloadManager.LibrarySync,
    folderLabel: String,
    callbacks: LibraryDownloadCallbacks,
    modifier: Modifier = Modifier,
) {
    val palette = LocalLumiPalette.current
    GlassPanel(modifier) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            Column(Modifier.weight(1f).padding(end = 12.dp)) {
                Text("Keep my whole library on this phone", style = MaterialTheme.typography.titleMedium)
                Text(
                    "Every cloud track, locked ones included, with its cover and details saved beside it. New uploads download on their own.",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }
            Switch(
                checked = prefs.downloadWholeLibrary,
                onCheckedChange = callbacks.onWholeLibrary,
                colors = SwitchDefaults.colors(checkedTrackColor = palette.accent),
            )
        }

        if (prefs.downloadWholeLibrary) {
            Spacer(Modifier.height(14.dp))
            val total = sync.total
            Text(
                if (total == 0) "Checking your cloud library…" else "%,d of %,d tracks on this phone".format(sync.saved, total),
                style = MaterialTheme.typography.labelLarge,
            )
            Spacer(Modifier.height(6.dp))
            if (total > 0) {
                LinearProgressIndicator(
                    progress = { (sync.saved.toFloat() / total).coerceIn(0f, 1f) },
                    color = palette.accent,
                    trackColor = palette.hairline,
                    modifier = Modifier.fillMaxWidth().height(6.dp).clip(CircleShape),
                )
            } else if (sync.running) {
                LinearProgressIndicator(color = palette.accent, trackColor = palette.hairline, modifier = Modifier.fillMaxWidth().height(6.dp).clip(CircleShape))
            }
            val status = when {
                sync.running -> "Downloading"
                sync.note != null -> sync.note
                total > 0 && sync.saved >= total -> "Up to date"
                else -> null
            }
            Row(Modifier.fillMaxWidth().padding(top = 6.dp), verticalAlignment = Alignment.CenterVertically) {
                Text(
                    status.orEmpty(),
                    style = MaterialTheme.typography.labelSmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    modifier = Modifier.weight(1f),
                )
                if (!sync.running) GlassButton("Sync now", Icons.Filled.Sync, onClick = callbacks.onSyncNow)
            }
        }

        Spacer(Modifier.height(12.dp))
        Hairline()
        Row(Modifier.fillMaxWidth().padding(vertical = 8.dp), verticalAlignment = Alignment.CenterVertically) {
            Column(Modifier.weight(1f)) {
                Text("Wi-Fi only", style = MaterialTheme.typography.titleSmall)
                Text(
                    "Hold library downloads until you're off mobile data",
                    style = MaterialTheme.typography.labelSmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }
            Switch(
                checked = prefs.wifiOnly,
                onCheckedChange = callbacks.onWifiOnly,
                colors = SwitchDefaults.colors(checkedTrackColor = palette.accent),
            )
        }
        Hairline()
        Row(
            Modifier.fillMaxWidth().padding(top = 8.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(8.dp),
        ) {
            Column(Modifier.weight(1f)) {
                Text("Saved to", style = MaterialTheme.typography.titleSmall)
                Text(folderLabel, style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
            }
            if (prefs.folderUri != null) TextButton(onClick = callbacks.onUsePhoneFolder) { Text("Reset") }
            GlassButton("Change", Icons.Filled.Folder, onClick = callbacks.onChooseFolder)
        }
        Text(
            "Artist › Album › track, each with a .json of its details and a .jpg cover. Locked tracks stay locked and play only in LumiMusic and Lumisound. Tracks already saved stay where they are.",
            style = MaterialTheme.typography.labelSmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            modifier = Modifier.padding(top = 8.dp),
        )
    }
}

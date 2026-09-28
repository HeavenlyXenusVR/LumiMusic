package com.lumisound.android.ui.screens.importer

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.CheckCircle
import androidx.compose.material.icons.filled.ErrorOutline
import androidx.compose.material.icons.filled.RadioButtonUnchecked
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.lumisound.android.AppContainer
import com.lumisound.android.cloud.ImportStage
import com.lumisound.android.cloud.StageResult
import kotlinx.coroutines.launch

/**
 * "Import my cloud data". Nothing is uploaded and nothing is merged: each stage
 * reads the account's own server-side data and replaces this device's mirror of
 * it, so running it twice is harmless and running it on a fresh install can never
 * push empty state over a mature account.
 */
@Composable
fun ImportScreen(container: AppContainer, onDismiss: () -> Unit) {
    val scope = rememberCoroutineScope()
    val progress by container.cloudImport.progress.collectAsStateWithLifecycle()

    AlertDialog(
        onDismissRequest = { if (!progress.running) onDismiss() },
        title = { Text("Import cloud data") },
        text = {
            Column {
                Text(
                    "Pulls this account's cloud tracks, favorites, playlists, play history " +
                        "and shared settings from the bridge onto this device.",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
                Spacer(Modifier.height(16.dp))
                ImportStage.entries.forEach { stage ->
                    StageRow(stage, progress.stages[stage] ?: StageResult.Pending)
                }
                progress.failures.takeIf { it.isNotEmpty() }?.let { failures ->
                    Spacer(Modifier.height(12.dp))
                    Text(
                        "Some parts didn't come through: " +
                            failures.joinToString { "${it.first.label} (${it.second})" } +
                            ". Everything else was still imported — you can retry just by running this again.",
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.error,
                    )
                }
            }
        },
        confirmButton = {
            TextButton(
                enabled = !progress.running,
                onClick = { scope.launch { container.cloudImport.import() } },
            ) {
                Text(if (progress.finishedAt == null) "Start import" else "Run again")
            }
        },
        dismissButton = {
            TextButton(enabled = !progress.running, onClick = onDismiss) { Text("Close") }
        },
    )
}

@Composable
private fun StageRow(stage: ImportStage, result: StageResult) {
    Row(
        modifier = Modifier.fillMaxWidth().padding(vertical = 6.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(10.dp),
    ) {
        when (result) {
            is StageResult.Running -> CircularProgressIndicator(Modifier.size(18.dp), strokeWidth = 2.dp)
            is StageResult.Done -> Icon(
                Icons.Filled.CheckCircle,
                contentDescription = null,
                tint = MaterialTheme.colorScheme.primary,
                modifier = Modifier.size(18.dp),
            )
            is StageResult.Failed -> Icon(
                Icons.Filled.ErrorOutline,
                contentDescription = null,
                tint = MaterialTheme.colorScheme.error,
                modifier = Modifier.size(18.dp),
            )
            StageResult.Pending -> Icon(
                Icons.Filled.RadioButtonUnchecked,
                contentDescription = null,
                tint = MaterialTheme.colorScheme.onSurfaceVariant,
                modifier = Modifier.size(18.dp),
            )
        }
        Column {
            Text(stage.label, style = MaterialTheme.typography.bodyMedium)
            val detail = when (result) {
                is StageResult.Done -> listOfNotNull(
                    if (result.count > 0) "${result.count} imported" else null,
                    result.note,
                ).joinToString(" · ").ifBlank { "nothing to import" }
                is StageResult.Failed -> result.message
                StageResult.Running -> "importing…"
                StageResult.Pending -> "waiting"
            }
            Text(
                detail,
                style = MaterialTheme.typography.labelSmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
        }
    }
}

package com.lumisound.android.ui.screens.eq

import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.FilterChip
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Slider
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.lumisound.android.AppContainer

/**
 * The device's own equalizer. Band count and centre frequencies come from the
 * hardware rather than a fixed curve this app invents, so what is shown is what is
 * actually being applied.
 */
@Composable
fun EqualizerScreen(container: AppContainer) {
    val state by container.equalizer.state.collectAsStateWithLifecycle()

    Column(
        Modifier
            .fillMaxSize()
            .verticalScroll(rememberScrollState())
            .padding(16.dp)
    ) {
        if (!state.available) {
            Text("Equalizer unavailable", style = MaterialTheme.typography.titleMedium)
            Spacer(Modifier.height(6.dp))
            Text(
                state.error?.let { "This device refused to provide one: $it" }
                    ?: "Start playing something — the equalizer attaches to the player's audio session, " +
                    "which only exists once playback has begun.",
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
            return@Column
        }

        Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
            Text("Equalizer", style = MaterialTheme.typography.titleMedium, modifier = Modifier.weight(1f))
            Switch(checked = state.enabled, onCheckedChange = container.equalizer::setEnabled)
        }

        Spacer(Modifier.height(12.dp))
        Row(
            Modifier.fillMaxWidth().horizontalScroll(rememberScrollState()),
            horizontalArrangement = Arrangement.spacedBy(8.dp),
        ) {
            state.presets.forEachIndexed { index, preset ->
                FilterChip(
                    selected = state.currentPreset == index,
                    onClick = { container.equalizer.applyPreset(index) },
                    label = { Text(preset) },
                )
            }
        }

        Spacer(Modifier.height(16.dp))
        state.bands.forEach { band ->
            Row(verticalAlignment = Alignment.CenterVertically) {
                Text(
                    band.centerFrequencyHz.asFrequencyLabel(),
                    style = MaterialTheme.typography.labelSmall,
                    modifier = Modifier.width(58.dp),
                )
                Slider(
                    value = band.levelMillibel.toFloat(),
                    valueRange = state.minLevelMillibel.toFloat()..state.maxLevelMillibel.toFloat(),
                    onValueChange = { container.equalizer.setBandLevel(band.index, it.toInt()) },
                    modifier = Modifier.weight(1f),
                )
                Text(
                    "%+.1f dB".format(band.levelMillibel / 100f),
                    style = MaterialTheme.typography.labelSmall,
                    modifier = Modifier.width(64.dp),
                )
            }
        }

        Spacer(Modifier.height(8.dp))
        TextButton(onClick = container.equalizer::resetFlat) { Text("Reset to flat") }

        Spacer(Modifier.height(16.dp))
        HorizontalDivider()
        Spacer(Modifier.height(16.dp))

        Text("Extra gain", style = MaterialTheme.typography.titleMedium)
        Text(
            "The cloud library's own loudness data can only ever turn a track down, so a quiet " +
                "track is brought back up here.",
            style = MaterialTheme.typography.labelSmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
        )
        Row(verticalAlignment = Alignment.CenterVertically) {
            Slider(
                value = state.bassBoostMillibel.toFloat(),
                valueRange = 0f..1_500f,
                onValueChange = { container.equalizer.setBassBoost(it.toInt()) },
                modifier = Modifier.weight(1f),
            )
            Text(
                "+%.1f dB".format(state.bassBoostMillibel / 100f),
                style = MaterialTheme.typography.labelSmall,
                modifier = Modifier.width(64.dp),
            )
        }
    }
}

private fun Int.asFrequencyLabel(): String = if (this >= 1_000) "${this / 1_000}k" else "$this"

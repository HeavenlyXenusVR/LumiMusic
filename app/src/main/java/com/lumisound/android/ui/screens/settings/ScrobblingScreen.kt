package com.lumisound.android.ui.screens.settings

import android.widget.Toast
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Audiotrack
import androidx.compose.material.icons.filled.GraphicEq
import androidx.compose.material.icons.filled.Radio
import androidx.compose.material.icons.filled.Sync
import androidx.compose.material3.Button
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.input.PasswordVisualTransformation
import androidx.compose.ui.unit.dp
import com.lumisound.android.AppContainer
import com.lumisound.android.bridge.model.ScrobbleUpdateRequest
import com.lumisound.android.ui.components.Hairline
import com.lumisound.android.ui.components.LoadableSection
import com.lumisound.android.ui.components.Pill
import com.lumisound.android.ui.components.SettingsGroup
import com.lumisound.android.ui.components.SettingsRow
import com.lumisound.android.ui.components.friendlyError
import com.lumisound.android.ui.components.rememberLoadable
import com.lumisound.android.ui.screens.stats.DetailHeader
import kotlinx.coroutines.launch

/**
 * Scrobbling is done by the bridge, off the same `POST /user/history` call every play
 * already makes -- so a play on Android scrobbles exactly like one on iOS, with no client
 * work beyond this switch.
 *
 * Last.fm and Libre.fm linking is an OAuth-style browser round trip that Lumisound drives;
 * their status shows here, and a link made on the phone applies to this app too.
 * ListenBrainz is a plain token, so it can be set from here.
 */
@Composable
fun ScrobblingScreen(container: AppContainer, onBack: () -> Unit) {
    val scope = rememberCoroutineScope()
    val context = LocalContext.current
    val api = container.http.discovery
    val links = rememberLoadable(Unit, "scrobble") { api.scrobbleLinks() }
    var token by remember { mutableStateOf("") }

    fun save(request: ScrobbleUpdateRequest, done: String) {
        scope.launch {
            try {
                api.updateScrobble(request)
                links.reload()
                Toast.makeText(context, done, Toast.LENGTH_SHORT).show()
            } catch (e: Exception) {
                Toast.makeText(context, friendlyError(e), Toast.LENGTH_SHORT).show()
            }
        }
    }

    Column(Modifier.fillMaxSize().verticalScroll(rememberScrollState()).padding(bottom = 28.dp)) {
        DetailHeader("Scrobbling", "Every finished play, sent on by your bridge", onBack)
        LoadableSection(links.state, onRetry = links::reload) { state ->
            SettingsGroup("Status") {
                SettingsRow(
                    icon = Icons.Filled.Sync,
                    title = "Scrobble plays",
                    subtitle = if (state.enabled) "On for every linked service" else "Paused",
                    trailing = {
                        Switch(checked = state.enabled, onCheckedChange = { enabled ->
                            save(ScrobbleUpdateRequest(enabled = enabled), if (enabled) "Scrobbling on" else "Scrobbling paused")
                        })
                    },
                )
            }
            Spacer(Modifier.height(20.dp))
            SettingsGroup("Services") {
                SettingsRow(
                    icon = Icons.Filled.Radio,
                    title = "Last.fm",
                    subtitle = state.lastfmUsername?.let { "Linked as $it" } ?: "Link from Lumisound on iPhone",
                    trailing = { if (state.lastfmLinked) Pill("Linked") },
                )
                Hairline()
                SettingsRow(
                    icon = Icons.Filled.Audiotrack,
                    title = "Libre.fm",
                    subtitle = state.librefmUsername?.let { "Linked as $it" } ?: "Link from Lumisound on iPhone",
                    trailing = { if (state.librefmLinked) Pill("Linked") },
                )
                Hairline()
                SettingsRow(
                    icon = Icons.Filled.GraphicEq,
                    title = "ListenBrainz",
                    subtitle = if (state.listenbrainzLinked) "Token saved" else "Paste your user token below",
                    trailing = { if (state.listenbrainzLinked) Pill("Linked") },
                )
                Column(Modifier.padding(horizontal = 16.dp, vertical = 10.dp)) {
                    OutlinedTextField(
                        value = token,
                        onValueChange = { token = it.trim() },
                        label = { Text("ListenBrainz user token") },
                        singleLine = true,
                        visualTransformation = PasswordVisualTransformation(),
                        keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Password),
                        shape = MaterialTheme.shapes.small,
                        modifier = Modifier.fillMaxWidth(),
                    )
                    Spacer(Modifier.height(8.dp))
                    Button(
                        onClick = {
                            save(ScrobbleUpdateRequest(listenbrainzToken = token, enabled = state.enabled), "ListenBrainz linked")
                            token = ""
                        },
                        enabled = token.length >= 8,
                        modifier = Modifier.fillMaxWidth(),
                    ) { Text("Save token") }
                    Text(
                        "Find it at listenbrainz.org → Settings. It is stored on your bridge, not on this device.",
                        style = MaterialTheme.typography.labelSmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                        modifier = Modifier.padding(top = 6.dp),
                    )
                }
            }
        }
    }
}

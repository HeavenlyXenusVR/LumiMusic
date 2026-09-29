package com.lumisound.android.ui.screens.social

import android.widget.Toast
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Favorite
import androidx.compose.material.icons.filled.MilitaryTech
import androidx.compose.material.icons.filled.PushPin
import androidx.compose.material.icons.filled.Person
import androidx.compose.material.icons.filled.Tag
import androidx.compose.material3.Button
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import com.lumisound.android.AppContainer
import com.lumisound.android.bridge.BridgeUrls
import com.lumisound.android.bridge.model.CompatibilityDto
import com.lumisound.android.bridge.model.FriendRequestCreate
import com.lumisound.android.bridge.model.ProfileDto
import com.lumisound.android.ui.components.IconSectionHeader
import com.lumisound.android.ui.components.LoadableSection
import com.lumisound.android.ui.components.LumiCard
import com.lumisound.android.ui.components.Pill
import com.lumisound.android.ui.components.friendlyError
import com.lumisound.android.ui.components.rememberLoadable
import com.lumisound.android.ui.components.trackSubtitle
import com.lumisound.android.ui.screens.stats.DetailHeader
import com.lumisound.android.ui.screens.stats.ProgressLine
import com.lumisound.android.ui.screens.stats.RankedRow
import com.lumisound.android.ui.searchAndPlay
import com.lumisound.android.ui.theme.LocalLumiPalette
import com.lumisound.android.ui.theme.SectionTint
import com.lumisound.android.ui.theme.parseAccent
import kotlinx.coroutines.launch

/**
 * Another listener's public profile: who they are, what they are into, their pinned
 * tracks, and -- between friends -- how well your taste matches, from the same
 * `/api/social/compatibility` score Lumisound's Music Match shows.
 */
@OptIn(ExperimentalLayoutApi::class)
@Composable
fun ProfileScreen(container: AppContainer, userId: String, name: String, onBack: () -> Unit) {
    val scope = rememberCoroutineScope()
    val context = LocalContext.current
    val api = container.http.social
    val profile = rememberLoadable(userId, "social.profile") { api.profile(userId) }
    // Friends only server-side; a stranger's 403 just leaves the section out.
    val match = rememberLoadable(userId, "social.match") { api.compatibility(userId) }
    val palette = LocalLumiPalette.current

    fun toast(text: String) = Toast.makeText(context, text, Toast.LENGTH_SHORT).show()

    Column(Modifier.fillMaxSize().verticalScroll(rememberScrollState()).padding(bottom = 28.dp)) {
        DetailHeader(name, null, onBack)
        LoadableSection(profile.state, onRetry = profile::reload) { p ->
            val accent = p.mainAccentHex?.let { parseAccent(it) } ?: palette.accent
            Box(
                Modifier
                    .fillMaxWidth()
                    .padding(horizontal = 16.dp)
                    .background(
                        Brush.verticalGradient(listOf(accent.copy(alpha = 0.35f), palette.elevatedSurface)),
                        MaterialTheme.shapes.large,
                    )
                    .padding(18.dp)
            ) {
                Column(horizontalAlignment = Alignment.CenterHorizontally, modifier = Modifier.fillMaxWidth()) {
                    Avatar(BridgeUrls.avatar(container.config.baseUrl, p.userId), p.username, 88.dp)
                    Spacer(Modifier.height(10.dp))
                    Text(p.shownName, style = MaterialTheme.typography.titleLarge)
                    Text(
                        listOfNotNull("@${p.username}", p.pronouns?.takeIf { it.isNotBlank() }).joinToString(" · "),
                        style = MaterialTheme.typography.labelMedium,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                    val status = listOfNotNull(p.statusEmoji, p.statusText).joinToString(" ").trim()
                    if (status.isNotEmpty()) {
                        Spacer(Modifier.height(8.dp))
                        Pill(status, tint = accent)
                    }
                    p.bio?.takeIf { it.isNotBlank() }?.let {
                        Spacer(Modifier.height(10.dp))
                        Text(it, style = MaterialTheme.typography.bodyMedium, textAlign = TextAlign.Center)
                    }
                    p.listeningStreak?.takeIf { it.currentStreakDays > 0 }?.let {
                        Spacer(Modifier.height(8.dp))
                        Text(
                            "🔥 ${it.currentStreakDays}-day listening streak",
                            style = MaterialTheme.typography.labelMedium,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                        )
                    }
                    Spacer(Modifier.height(14.dp))
                    FriendButton(p, onAdd = {
                        scope.launch {
                            try {
                                api.sendRequest(FriendRequestCreate(toUserId = p.userId))
                                toast("Request sent to ${p.shownName}")
                            } catch (e: Exception) {
                                toast(friendlyError(e))
                            }
                        }
                    }, onRemove = {
                        scope.launch {
                            try {
                                api.removeFriend(p.userId)
                                toast("Removed ${p.shownName} from friends")
                                profile.reload()
                            } catch (e: Exception) {
                                toast(friendlyError(e))
                            }
                        }
                    })
                }
            }

            match.state.valueOrNull?.let { MatchCard(it) }

            if (p.badges.isNotEmpty()) {
                IconSectionHeader(Icons.Filled.MilitaryTech, "Badges", tint = SectionTint.Device)
                FlowRow(
                    Modifier.fillMaxWidth().padding(horizontal = 16.dp),
                    horizontalArrangement = Arrangement.spacedBy(8.dp),
                    verticalArrangement = Arrangement.spacedBy(8.dp),
                ) {
                    p.badges.forEach { badge -> Pill(badge.label, tint = badgeTint(badge.tier)) }
                }
            }

            if (p.topArtists.isNotEmpty()) {
                IconSectionHeader(Icons.Filled.Person, "Top artists", tint = SectionTint.Favorites)
                p.topArtists.forEachIndexed { index, artist -> RankedRow(index + 1, artist, null, "") }
            }
            if (p.topGenres.isNotEmpty()) {
                IconSectionHeader(Icons.Filled.Tag, "Genres", tint = SectionTint.Offline)
                FlowRow(
                    Modifier.fillMaxWidth().padding(horizontal = 16.dp),
                    horizontalArrangement = Arrangement.spacedBy(8.dp),
                    verticalArrangement = Arrangement.spacedBy(8.dp),
                ) { p.topGenres.forEach { Pill(it) } }
            }
            if (p.pinnedTracks.isNotEmpty()) {
                IconSectionHeader(Icons.Filled.PushPin, "Pinned tracks", tint = SectionTint.Playlists)
                p.pinnedTracks.forEachIndexed { index, pin ->
                    val title = pin.title ?: return@forEachIndexed
                    RankedRow(index + 1, title, trackSubtitle(title, pin.artist, pin.album), "Play", onClick = {
                        scope.launch { if (!container.searchAndPlay(title, pin.artist)) toast("No match for $title.") }
                    })
                }
            }
        }
    }
}

@Composable
private fun FriendButton(profile: ProfileDto, onAdd: () -> Unit, onRemove: () -> Unit) {
    if (profile.isFriend) {
        OutlinedButton(onClick = onRemove) { Text("Remove friend") }
    } else {
        Button(onClick = onAdd) { Text("Add friend") }
    }
}

@Composable
private fun MatchCard(match: CompatibilityDto) {
    IconSectionHeader(Icons.Filled.Favorite, "Music match", tint = SectionTint.Favorites)
    LumiCard(Modifier.fillMaxWidth().padding(horizontal = 16.dp)) {
        Column(Modifier.padding(16.dp)) {
            if (match.insufficientData) {
                Text(
                    "Not enough listening yet on one side to compare.",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
                return@Column
            }
            Row(verticalAlignment = Alignment.Bottom) {
                Text("${match.score}%", style = MaterialTheme.typography.displaySmall, color = LocalLumiPalette.current.accent)
                Text(
                    "  taste match",
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    modifier = Modifier.padding(bottom = 6.dp),
                )
            }
            ProgressLine(match.score / 100f)
            match.reasons.forEach {
                Spacer(Modifier.height(6.dp))
                Text("• $it", style = MaterialTheme.typography.bodySmall)
            }
            if (match.sharedArtists.isNotEmpty()) {
                Spacer(Modifier.height(10.dp))
                Text(
                    "Both into: ${match.sharedArtists.take(8).joinToString(", ")}",
                    style = MaterialTheme.typography.labelMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }
        }
    }
}

private fun badgeTint(tier: String?) = when (tier) {
    "gold" -> androidx.compose.ui.graphics.Color(0xFFF6C744)
    "silver" -> androidx.compose.ui.graphics.Color(0xFFCBD5E0)
    "bronze" -> androidx.compose.ui.graphics.Color(0xFFDD9A5B)
    else -> null
}

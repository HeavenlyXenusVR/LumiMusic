package com.lumisound.android.ui.components

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.horizontalScroll
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
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardActions
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.ChevronRight
import androidx.compose.material.icons.filled.Search
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.material3.TextField
import androidx.compose.material3.TextFieldDefaults
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.unit.dp
import com.lumisound.android.ui.theme.LocalLumiPalette

/**
 * The floating pill of icon actions that sits above each screen's title, as on iOS.
 *
 * Centred and detached from the edges on purpose: it reads as a control cluster rather than
 * a title bar, which is what lets the large title below it be the actual heading.
 */
@Composable
fun CapsuleToolbar(
    actions: List<ToolbarAction>,
    modifier: Modifier = Modifier,
) {
    val palette = LocalLumiPalette.current
    Row(
        modifier
            .padding(vertical = 8.dp)
            .clip(CircleShape)
            .background(palette.elevatedSurface.copy(alpha = 0.92f))
            .border(1.dp, palette.hairline, CircleShape)
            .padding(horizontal = 6.dp, vertical = 4.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(2.dp),
    ) {
        actions.forEach { action ->
            Box(
                Modifier
                    .size(40.dp)
                    .clip(CircleShape)
                    .then(if (action.selected) Modifier.background(palette.accentWash) else Modifier)
                    .clickable(onClick = action.onClick),
                contentAlignment = Alignment.Center,
            ) {
                Icon(
                    action.icon,
                    contentDescription = action.label,
                    tint = if (action.selected) palette.accent else palette.accent.copy(alpha = 0.82f),
                    modifier = Modifier.size(19.dp),
                )
            }
        }
    }
}

data class ToolbarAction(
    val icon: ImageVector,
    val label: String,
    val selected: Boolean = false,
    val onClick: () -> Unit = {},
)

/** The large screen heading, with an optional line under it. */
@Composable
fun ScreenTitle(title: String, subtitle: String? = null, modifier: Modifier = Modifier) {
    Column(modifier.padding(start = 20.dp, end = 20.dp, top = 2.dp, bottom = 10.dp)) {
        Text(title, style = MaterialTheme.typography.displaySmall)
        subtitle?.let {
            Text(
                it,
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
        }
    }
}

/** The filled, fully-rounded search field used at the top of every library screen. */
@Composable
fun SearchField(
    value: String,
    onValueChange: (String) -> Unit,
    placeholder: String,
    modifier: Modifier = Modifier,
    /** When set, the keyboard shows a search key that calls this. */
    onSubmit: (() -> Unit)? = null,
) {
    val palette = LocalLumiPalette.current
    TextField(
        value = value,
        onValueChange = onValueChange,
        placeholder = {
            Text(
                placeholder,
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
        },
        leadingIcon = {
            Icon(
                Icons.Filled.Search,
                contentDescription = null,
                tint = MaterialTheme.colorScheme.onSurfaceVariant,
                modifier = Modifier.size(19.dp),
            )
        },
        singleLine = true,
        keyboardOptions = if (onSubmit != null) KeyboardOptions(imeAction = ImeAction.Search) else KeyboardOptions.Default,
        keyboardActions = KeyboardActions(onSearch = { onSubmit?.invoke() }),
        shape = CircleShape,
        colors = TextFieldDefaults.colors(
            focusedContainerColor = palette.elevatedSurface,
            unfocusedContainerColor = palette.elevatedSurface,
            focusedIndicatorColor = Color.Transparent,
            unfocusedIndicatorColor = Color.Transparent,
            cursorColor = palette.accent,
        ),
        modifier = modifier
            .fillMaxWidth()
            .padding(horizontal = 16.dp)
            .height(52.dp),
    )
}

/** One entry in the scrolling chip row beneath the search field. */
data class NavChip(val label: String, val icon: ImageVector, val selected: Boolean, val onClick: () -> Unit)

@Composable
fun ChipRow(chips: List<NavChip>, modifier: Modifier = Modifier) {
    val palette = LocalLumiPalette.current
    Row(
        modifier
            .fillMaxWidth()
            .horizontalScroll(rememberScrollState())
            .padding(horizontal = 16.dp, vertical = 12.dp),
        horizontalArrangement = Arrangement.spacedBy(9.dp),
    ) {
        chips.forEach { chip ->
            Row(
                Modifier
                    .clip(CircleShape)
                    .background(if (chip.selected) palette.accent else palette.elevatedSurface)
                    .clickable(onClick = chip.onClick)
                    .padding(horizontal = 14.dp, vertical = 9.dp),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(6.dp),
            ) {
                Icon(
                    chip.icon,
                    contentDescription = null,
                    tint = if (chip.selected) Color.White else MaterialTheme.colorScheme.onSurfaceVariant,
                    modifier = Modifier.size(15.dp),
                )
                Text(
                    chip.label,
                    style = MaterialTheme.typography.labelLarge,
                    color = if (chip.selected) Color.White else MaterialTheme.colorScheme.onSurface,
                )
            }
        }
    }
}

/**
 * A section heading with the small tinted icon tile Lumisound puts beside each one, and an
 * optional "See All" on the right.
 */
@Composable
fun IconSectionHeader(
    icon: ImageVector,
    title: String,
    modifier: Modifier = Modifier,
    tint: Color? = null,
    onSeeAll: (() -> Unit)? = null,
) {
    val palette = LocalLumiPalette.current
    val color = tint ?: palette.accent
    Row(
        modifier.fillMaxWidth().padding(start = 20.dp, end = 20.dp, top = 16.dp, bottom = 8.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Box(
            Modifier
                .size(26.dp)
                .clip(RoundedCornerShape(8.dp))
                .background(color.copy(alpha = 0.18f)),
            contentAlignment = Alignment.Center,
        ) {
            Icon(icon, contentDescription = null, tint = color, modifier = Modifier.size(15.dp))
        }
        Spacer(Modifier.width(10.dp))
        Text(title, style = MaterialTheme.typography.titleMedium, modifier = Modifier.weight(1f))
        onSeeAll?.let {
            Row(
                Modifier.clip(CircleShape).clickable(onClick = it).padding(horizontal = 8.dp, vertical = 4.dp),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                Text("See All", style = MaterialTheme.typography.labelMedium, color = palette.accent)
                Icon(
                    Icons.Filled.ChevronRight,
                    contentDescription = null,
                    tint = palette.accent,
                    modifier = Modifier.size(15.dp),
                )
            }
        }
    }
}

/** The small stat capsules under an album or playlist header. */
@Composable
fun StatPills(stats: List<Pair<ImageVector, String>>, modifier: Modifier = Modifier) {
    val palette = LocalLumiPalette.current
    Row(modifier, horizontalArrangement = Arrangement.spacedBy(8.dp)) {
        stats.forEach { (icon, text) ->
            Row(
                Modifier
                    .clip(CircleShape)
                    .background(palette.elevatedSurface)
                    .padding(horizontal = 12.dp, vertical = 7.dp),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(6.dp),
            ) {
                Icon(
                    icon,
                    contentDescription = null,
                    tint = MaterialTheme.colorScheme.onSurfaceVariant,
                    modifier = Modifier.size(14.dp),
                )
                Text(text, style = MaterialTheme.typography.labelMedium)
            }
        }
    }
}

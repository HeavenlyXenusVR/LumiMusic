package com.lumisound.android.ui.components

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.MusicNote
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import coil3.compose.SubcomposeAsyncImage
import com.lumisound.android.ui.theme.LocalLumiPalette

/**
 * Artwork with a deterministic fallback.
 *
 * Most of a cloud library has no embedded art, and a grid of identical grey note icons
 * is the single thing that made the first build look unfinished. The fallback derives a
 * gradient from the track's own key, so a library without artwork still reads as a set
 * of distinct things rather than one repeated placeholder.
 */
@Composable
fun Artwork(
    model: Any?,
    fallbackKey: String,
    size: Dp,
    modifier: Modifier = Modifier,
    corner: Dp = 10.dp,
) {
    val shape = RoundedCornerShape(corner)
    Box(modifier.size(size).clip(shape)) {
        FallbackArt(fallbackKey, Modifier.fillMaxSize())
        if (model != null) {
            SubcomposeAsyncImage(
                model = model,
                contentDescription = null,
                contentScale = ContentScale.Crop,
                modifier = Modifier.fillMaxSize(),
                // No spinner and no error glyph: the fallback underneath is already a
                // finished-looking thing, so a failed load simply stays as it is.
                loading = {},
                error = {},
            )
        }
    }
}

@Composable
fun FallbackArt(key: String, modifier: Modifier = Modifier) {
    val palette = fallbackPaletteFor(key)
    val top = Color.hsl(palette.hue, palette.saturation, palette.lightness)
    val bottom = Color.hsl((palette.hue + 38f) % 360f, palette.saturation + 0.04f, palette.lightness - 0.14f)
    Box(
        modifier.background(Brush.linearGradient(listOf(top, bottom))),
        contentAlignment = Alignment.Center,
    ) {
        Icon(
            Icons.Filled.MusicNote,
            contentDescription = null,
            tint = Color.White.copy(alpha = 0.28f),
        )
    }
}

/**
 * Hue, saturation and lightness for a key's generated artwork.
 *
 * `hashCode() % 360` looked fine in code and was wrong on screen: Java's string hash for
 * two nearly-identical keys differs by a tiny amount, so "Title 1" and "Title 2" -- or a
 * row of tracks from one folder -- came out the same colour, which is exactly what a wall
 * of identical green shelf cards looked like. Mixing the bits first (a 32-bit avalanche,
 * the same trick hash tables use) sends near-identical inputs to unrelated hues, and two
 * further slices of the mixed value vary saturation and lightness so neighbours differ by
 * more than hue alone.
 */
data class FallbackPalette(val hue: Float, val saturation: Float, val lightness: Float)

fun fallbackPaletteFor(key: String): FallbackPalette {
    var x = key.hashCode()
    x = x xor (x ushr 16)
    x *= 0x7feb352d
    x = x xor (x ushr 15)
    x *= 0x846ca68b
    x = x xor (x ushr 16)
    val mixed = x.toLong() and 0xFFFFFFFFL
    return FallbackPalette(
        hue = (mixed % 360L).toFloat(),
        saturation = 0.34f + ((mixed shr 9) % 22L) / 100f,
        lightness = 0.26f + ((mixed shr 17) % 14L) / 100f,
    )
}

/** A section title with an optional trailing action, used above every list and shelf. */
@Composable
fun SectionHeader(
    title: String,
    modifier: Modifier = Modifier,
    subtitle: String? = null,
    action: (@Composable () -> Unit)? = null,
) {
    Row(
        modifier.fillMaxWidth().padding(horizontal = 20.dp, vertical = 10.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Column(Modifier.weight(1f)) {
            Text(title, style = MaterialTheme.typography.titleMedium)
            subtitle?.let {
                Text(
                    it,
                    style = MaterialTheme.typography.labelSmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }
        }
        action?.invoke()
    }
}

/** The app's card: a soft raised surface with a hairline instead of a shadow. */
@Composable
fun LumiCard(
    modifier: Modifier = Modifier,
    onClick: (() -> Unit)? = null,
    content: @Composable () -> Unit,
) {
    val palette = LocalLumiPalette.current
    Surface(
        modifier = modifier.then(if (onClick != null) Modifier.clickable(onClick = onClick) else Modifier),
        shape = MaterialTheme.shapes.medium,
        color = palette.elevatedSurface,
        border = BorderStroke(1.dp, palette.hairline),
        content = { content() },
    )
}

/** A labelled row inside a settings group: icon, title, supporting line, trailing slot. */
@Composable
fun SettingsRow(
    icon: ImageVector,
    title: String,
    subtitle: String? = null,
    onClick: (() -> Unit)? = null,
    trailing: (@Composable () -> Unit)? = null,
) {
    val palette = LocalLumiPalette.current
    Row(
        Modifier
            .fillMaxWidth()
            .then(if (onClick != null) Modifier.clickable(onClick = onClick) else Modifier)
            .padding(horizontal = 16.dp, vertical = 14.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(14.dp),
    ) {
        Box(
            Modifier.size(34.dp).clip(RoundedCornerShape(10.dp)).background(palette.accentWash),
            contentAlignment = Alignment.Center,
        ) {
            Icon(icon, contentDescription = null, tint = palette.accent, modifier = Modifier.size(18.dp))
        }
        Column(Modifier.weight(1f)) {
            Text(title, style = MaterialTheme.typography.bodyLarge)
            subtitle?.let {
                Text(
                    it,
                    style = MaterialTheme.typography.labelSmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }
        }
        trailing?.invoke()
    }
}

/** Groups settings rows into one card, with hairlines between them. */
@Composable
fun SettingsGroup(title: String, content: @Composable () -> Unit) {
    Column(Modifier.fillMaxWidth().padding(horizontal = 16.dp)) {
        Text(
            title.uppercase(),
            style = MaterialTheme.typography.labelSmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            modifier = Modifier.padding(start = 6.dp, bottom = 8.dp),
        )
        LumiCard(Modifier.fillMaxWidth()) { Column { content() } }
    }
}

@Composable
fun Hairline(modifier: Modifier = Modifier) {
    Box(
        modifier
            .fillMaxWidth()
            .height(1.dp)
            .background(LocalLumiPalette.current.hairline)
    )
}

/** A small filled pill, used for counts and state labels. */
@Composable
fun Pill(text: String, modifier: Modifier = Modifier, tint: Color? = null) {
    val palette = LocalLumiPalette.current
    Box(
        modifier
            .clip(CircleShape)
            .background(tint?.copy(alpha = 0.16f) ?: palette.accentWash)
            .padding(horizontal = 9.dp, vertical = 3.dp)
    ) {
        Text(
            text,
            style = MaterialTheme.typography.labelSmall,
            color = tint ?: palette.accent,
        )
    }
}

/** The shared "there is nothing here" state, with room for one action. */
@Composable
fun EmptyState(
    icon: ImageVector,
    title: String,
    message: String,
    modifier: Modifier = Modifier,
    action: (@Composable () -> Unit)? = null,
) {
    val palette = LocalLumiPalette.current
    Box(modifier.fillMaxSize().padding(32.dp), contentAlignment = Alignment.Center) {
        Column(horizontalAlignment = Alignment.CenterHorizontally) {
            Box(
                Modifier.size(72.dp).clip(CircleShape).background(palette.accentWash),
                contentAlignment = Alignment.Center,
            ) {
                Icon(icon, contentDescription = null, tint = palette.accent, modifier = Modifier.size(30.dp))
            }
            Spacer(Modifier.height(18.dp))
            Text(title, style = MaterialTheme.typography.titleMedium, textAlign = TextAlign.Center)
            Spacer(Modifier.height(6.dp))
            Text(
                message,
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                textAlign = TextAlign.Center,
            )
            action?.let {
                Spacer(Modifier.height(20.dp))
                it()
            }
        }
    }
}

/** One line of text that never wraps, used everywhere a title or subtitle appears. */
@Composable
fun OneLine(
    text: String,
    style: androidx.compose.ui.text.TextStyle = MaterialTheme.typography.bodyLarge,
    color: Color = Color.Unspecified,
    modifier: Modifier = Modifier,
) {
    Text(text, style = style, color = color, maxLines = 1, overflow = TextOverflow.Ellipsis, modifier = modifier)
}

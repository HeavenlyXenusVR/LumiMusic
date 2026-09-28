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
import androidx.compose.runtime.LaunchedEffect
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
import com.lumisound.android.diagnostics.AppLogger
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
                // finished-looking thing, so a failed load simply stays as it is. It is
                // still counted, because "no artwork anywhere" looked identical to "this
                // library has no artwork" for an entire release.
                loading = {},
                // Counted from inside the error slot, keyed on the model so one failed
                // image is recorded once rather than on every recomposition.
                error = { LaunchedEffect(model) { noteArtworkFailure(model) } },
            )
        }
    }
}

@Composable
fun FallbackArt(key: String, modifier: Modifier = Modifier) = BlobArt(key, modifier)

private val artworkFailures = java.util.concurrent.atomic.AtomicInteger()

/**
 * Logs the first failure and every twenty-fifth after it. A library of a few thousand rows
 * failing to load would otherwise fill the whole log ring with one repeated line and push
 * out everything that explains why.
 */
private fun noteArtworkFailure(model: Any?) {
    val count = artworkFailures.incrementAndGet()
    if (count == 1 || count % 25 == 0) {
        AppLogger.w(
            "artwork",
            "image load failed ($count so far)",
            mapOf("model" to model?.toString()?.substringBefore('?')?.takeLast(90)),
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
/**
 * The hue bands generated covers are allowed to use.
 *
 * A uniform 0-360 spread looked right in code and muddy on screen: a third of the wheel is
 * olive and khaki, which is not a colour any cover in Lumisound's artwork language uses.
 * Picking a band first and placing the hue inside it keeps every generated cover in the
 * magenta / violet / blue / cyan / amber family the real artwork lives in.
 */
private val HUE_BANDS = listOf(
    300f to 330f, 330f to 356f,   // magenta into pink
    268f to 298f, 240f to 268f,   // violet into indigo
    206f to 238f, 186f to 206f,   // blue into azure
    164f to 186f,                 // cyan and teal
    20f to 42f, 42f to 58f,       // amber into gold
    352f to 374f,                 // coral, wrapping past red
)

data class FallbackPalette(val hue: Float, val saturation: Float, val lightness: Float)

fun fallbackPaletteFor(key: String): FallbackPalette {
    // Both constants exceed Int.MAX_VALUE, so they are written unsigned and converted --
    // as plain hex literals Kotlin types them Long and the multiplication will not compile.
    var x = key.hashCode()
    x = x xor (x ushr 16)
    x *= 0x7feb352du.toInt()
    x = x xor (x ushr 15)
    x *= 0x846ca68bu.toInt()
    x = x xor (x ushr 16)
    val mixed = x.toLong() and 0xFFFFFFFFL
    // Band from the high bits and position within it from the low ones, so two keys that
    // happen to share a band are still unlikely to share a place inside it.
    val (bandStart, bandEnd) = HUE_BANDS[((mixed shr 24) % HUE_BANDS.size).toInt()]
    val within = (mixed % 1000L) / 1000f
    // These ranges are measured, not guessed: a model of this function over a 360-key
    // library put near-identical pairs at 12.9% with the first values tried, and these
    // bring it to 5.5% while keeping fields dark enough for a bright orb to read against.
    return FallbackPalette(
        hue = (bandStart + (bandEnd - bandStart) * within) % 360f,
        saturation = 0.50f + ((mixed shr 9) % 32L) / 100f,
        lightness = 0.22f + ((mixed shr 17) % 24L) / 100f,
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

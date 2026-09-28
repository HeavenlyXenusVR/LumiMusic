package com.lumisound.android.ui.components

import androidx.compose.foundation.Canvas
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import kotlin.math.max

/**
 * The generated cover art: a soft glowing orb on a tinted field, matching Lumisound's own
 * artwork language rather than the flat two-stop gradient this app started with.
 *
 * Every value is derived from the track's key, so the same track is always the same cover
 * and neighbouring tracks are not neighbouring colours -- see [fallbackPaletteFor], which
 * mixes the hash first because Java's string hash puts near-identical keys next to each
 * other and produced a whole shelf in one shade of green.
 */
@Composable
fun BlobArt(key: String, modifier: Modifier = Modifier) {
    val palette = fallbackPaletteFor(key)
    val spec = blobSpecFor(key, palette)

    Canvas(modifier) {
        val shortest = max(size.minDimension, 1f)
        drawRect(
            Brush.linearGradient(
                listOf(spec.fieldTop, spec.fieldBottom),
                start = Offset(0f, 0f),
                end = Offset(size.width, size.height),
            )
        )
        // The orb: a wide radial falloff, deliberately soft-edged and off-centre, with a
        // tighter highlight inside it so it reads as lit rather than as a flat disc.
        drawCircle(
            brush = Brush.radialGradient(
                colors = listOf(spec.orb, spec.orb.copy(alpha = 0.55f), Color.Transparent),
                center = Offset(size.width * spec.centerX, size.height * spec.centerY),
                radius = shortest * spec.radius,
            ),
            radius = shortest * spec.radius,
            center = Offset(size.width * spec.centerX, size.height * spec.centerY),
        )
        drawCircle(
            brush = Brush.radialGradient(
                colors = listOf(spec.highlight.copy(alpha = 0.85f), Color.Transparent),
                center = Offset(size.width * spec.centerX, size.height * (spec.centerY - 0.05f)),
                radius = shortest * spec.radius * 0.55f,
            ),
            radius = shortest * spec.radius * 0.55f,
            center = Offset(size.width * spec.centerX, size.height * (spec.centerY - 0.05f)),
        )
    }
}

/**
 * Four blobs in one tile, for a playlist or folder -- the same collage Lumisound uses for a
 * group of things rather than one cover standing in for all of them.
 */
@Composable
fun CollageArt(keys: List<String>, size: Dp, modifier: Modifier = Modifier, corner: Dp = 14.dp) {
    val filled = when {
        keys.isEmpty() -> List(4) { "empty-$it" }
        keys.size >= 4 -> keys.take(4)
        // Fewer than four: repeat what there is rather than leaving holes, which is what a
        // half-empty grid of placeholder tiles looks like.
        else -> List(4) { keys[it % keys.size] + "#$it" }
    }
    Box(modifier.size(size).clip(RoundedCornerShape(corner))) {
        androidx.compose.foundation.layout.Column(Modifier.fillMaxSize()) {
            androidx.compose.foundation.layout.Row(Modifier.weight(1f)) {
                BlobArt(filled[0], Modifier.weight(1f).fillMaxSize())
                BlobArt(filled[1], Modifier.weight(1f).fillMaxSize())
            }
            androidx.compose.foundation.layout.Row(Modifier.weight(1f)) {
                BlobArt(filled[2], Modifier.weight(1f).fillMaxSize())
                BlobArt(filled[3], Modifier.weight(1f).fillMaxSize())
            }
        }
    }
}

private data class BlobSpec(
    val fieldTop: Color,
    val fieldBottom: Color,
    val orb: Color,
    val highlight: Color,
    val centerX: Float,
    val centerY: Float,
    val radius: Float,
)

private fun blobSpecFor(key: String, palette: FallbackPalette): BlobSpec {
    // A second, independent mix so position and size do not track the colour: two tracks
    // with similar hues should still differ in where their orb sits.
    var x = (key.hashCode() * 31 + key.length)
    x = x xor (x ushr 13)
    x *= 0x5bd1e995u.toInt()
    x = x xor (x ushr 15)
    val mixed = x.toLong() and 0xFFFFFFFFL

    val hue = palette.hue
    // The orb sits a little around the wheel from its field, the way a lit subject picks up
    // a neighbouring colour rather than being a brighter copy of the background.
    val orbHue = (hue + 12f + (mixed % 30L)) % 360f
    return BlobSpec(
        fieldTop = Color.hsl(hue, palette.saturation, palette.lightness + 0.10f),
        fieldBottom = Color.hsl((hue + 22f) % 360f, palette.saturation, (palette.lightness - 0.08f).coerceAtLeast(0.07f)),
        orb = Color.hsl(orbHue, (palette.saturation + 0.26f).coerceAtMost(0.95f), 0.66f),
        highlight = Color.hsl(orbHue, 0.62f, 0.86f),
        centerX = 0.32f + ((mixed shr 5) % 36L) / 100f,
        centerY = 0.30f + ((mixed shr 11) % 40L) / 100f,
        radius = 0.38f + ((mixed shr 19) % 18L) / 100f,
    )
}

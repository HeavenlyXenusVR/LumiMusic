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

/**
 * A cover's colours as plain HSL numbers, so the same values the canvas draws can be
 * measured directly without standing up the UI stack.
 */
data class CoverSpec(
    val fieldHue: Float,
    val fieldSaturation: Float,
    val fieldLightness: Float,
    val orbHue: Float,
    val orbSaturation: Float,
    val orbLightness: Float,
    val centerX: Float,
    val centerY: Float,
    val radius: Float,
)

fun coverSpecFor(key: String): CoverSpec {
    val palette = fallbackPaletteFor(key)
    var x = (key.hashCode() * 31 + key.length)
    x = x xor (x ushr 13)
    x *= 0x5bd1e995u.toInt()
    x = x xor (x ushr 15)
    val mixed = x.toLong() and 0xFFFFFFFFL
    return CoverSpec(
        fieldHue = palette.hue,
        fieldSaturation = palette.saturation,
        fieldLightness = palette.lightness,
        // The orb roams a good way around the wheel from its field rather than being a
        // brighter copy of it -- that offset is most of what separates two covers whose
        // fields happen to land in the same band.
        orbHue = (palette.hue + 10f + (mixed % 160L)) % 360f,
        orbSaturation = (palette.saturation + 0.24f).coerceAtMost(0.95f),
        orbLightness = 0.56f + ((mixed shr 7) % 30L) / 100f,
        centerX = 0.32f + ((mixed shr 5) % 36L) / 100f,
        centerY = 0.30f + ((mixed shr 11) % 40L) / 100f,
        radius = 0.38f + ((mixed shr 19) % 18L) / 100f,
    )
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
    val spec = coverSpecFor(key)
    return BlobSpec(
        fieldTop = Color.hsl(spec.fieldHue, spec.fieldSaturation, spec.fieldLightness + 0.06f),
        fieldBottom = Color.hsl(
            (spec.fieldHue + 22f) % 360f,
            spec.fieldSaturation,
            (spec.fieldLightness - 0.06f).coerceAtLeast(0.07f),
        ),
        orb = Color.hsl(spec.orbHue, spec.orbSaturation, spec.orbLightness),
        highlight = Color.hsl(spec.orbHue, 0.62f, (spec.orbLightness + 0.20f).coerceAtMost(0.92f)),
        centerX = spec.centerX,
        centerY = spec.centerY,
        radius = spec.radius,
    )
}

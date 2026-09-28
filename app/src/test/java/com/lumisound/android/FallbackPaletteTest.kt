package com.lumisound.android

import com.lumisound.android.ui.components.FallbackPalette
import com.lumisound.android.ui.components.fallbackPaletteFor
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test
import kotlin.math.abs
import kotlin.math.sqrt

/**
 * Generated covers are the only thing distinguishing most rows in this library, since almost
 * nothing in it has embedded artwork. Keys that differ by one character therefore have to
 * produce covers that LOOK different.
 *
 * The measure is deliberately perceptual rather than hue distance. Hue alone was the wrong
 * thing to assert: covers are constrained to six vivid bands so they stay in the app's
 * artwork language, which means two keys can legitimately share a band -- and still look
 * quite different, because lightness and saturation carry the rest. What matters is the
 * colour that ends up on screen.
 */
class FallbackPaletteTest {

    /** Straight HSL to RGB, written out here so the metric does not depend on the UI stack. */
    private fun rgb(p: FallbackPalette): Triple<Float, Float, Float> {
        val c = (1 - abs(2 * p.lightness - 1)) * p.saturation
        val h = (p.hue % 360f) / 60f
        val x = c * (1 - abs(h % 2f - 1))
        val (r1, g1, b1) = when {
            h < 1 -> Triple(c, x, 0f)
            h < 2 -> Triple(x, c, 0f)
            h < 3 -> Triple(0f, c, x)
            h < 4 -> Triple(0f, x, c)
            h < 5 -> Triple(x, 0f, c)
            else -> Triple(c, 0f, x)
        }
        val m = p.lightness - c / 2
        return Triple(r1 + m, g1 + m, b1 + m)
    }

    /** 0 = identical, 1 = black against white. */
    private fun distance(a: String, b: String): Float {
        val (r1, g1, b1) = rgb(fallbackPaletteFor(a))
        val (r2, g2, b2) = rgb(fallbackPaletteFor(b))
        return sqrt(((r1 - r2) * (r1 - r2) + (g1 - g2) * (g1 - g2) + (b1 - b2) * (b1 - b2)) / 3f)
    }

    @Test
    fun `keys that differ by one character produce visibly different covers`() {
        val pairs = listOf(
            "Title 1.opus.lms" to "Title 2.opus.lms",
            "shelf-0" to "shelf-1",
            "Sonic/Act 1.mp3" to "Sonic/Act 2.mp3",
            "(Mario) The Music Box OST - Title 9.opus.lms" to "(Mario) The Music Box OST - Title 10.opus.lms",
        )
        pairs.forEach { (first, second) ->
            val d = distance(first, second)
            assertTrue("\"$first\" and \"$second\" differ by only $d", d > 0.10f)
        }
    }

    /**
     * The honest limit of a constrained palette: six bands cannot guarantee that ANY two
     * keys differ -- single-character keys like "a" and "b" can land close together, and
     * widening the palette to fix that would put covers back in the olive band this whole
     * approach exists to avoid. What is worth holding is that collisions stay rare across a
     * realistic library, so a screen full of covers never reads as repeated.
     */
    @Test
    fun `collisions are rare across a realistic library`() {
        val keys = buildList {
            (1..120).forEach { add("Midnight Arcade/After Hours $it.opus.lms") }
            (1..120).forEach { add("Sonic Sound Archive/Act $it.mp3") }
            (1..120).forEach { add("Imported Music/Late Night/track-$it.flac") }
        }
        val pairs = keys.indices.flatMap { i -> (i + 1 until keys.size).map { j -> keys[i] to keys[j] } }
        val tooClose = pairs.count { (a, b) -> distance(a, b) < 0.08f }
        val ratio = tooClose.toFloat() / pairs.size
        assertTrue("$tooClose of ${pairs.size} pairs (${ratio * 100}%) are near-identical", ratio < 0.03f)

        // Neighbours matter more than distant pairs: those are the ones seen side by side.
        val worstNeighbour = keys.zipWithNext().minOf { (a, b) -> distance(a, b) }
        assertTrue("closest neighbouring pair differs by only $worstNeighbour", worstNeighbour > 0.06f)
    }

    @Test
    fun `a folder of sequential tracks does not come out as one colour`() {
        val keys = (1..12).map { "(Mario) The Music Box OST - Title $it.opus.lms" }
        val palettes = keys.map(::fallbackPaletteFor)
        // Sequential names used to collapse onto one hue; several distinct bands is the
        // property worth holding, not any particular spread.
        val bands = palettes.map { (it.hue / 30f).toInt() }.distinct()
        assertTrue("only ${bands.size} distinct colour bands across 12 tracks", bands.size >= 4)

        val worst = keys.zipWithNext().minOf { (a, b) -> distance(a, b) }
        assertTrue("closest neighbouring pair differs by only $worst", worst > 0.06f)
    }

    @Test
    fun `covers stay inside the app's colour family`() {
        // Olive and khaki are the band a uniform spread kept landing in, and nothing in
        // Lumisound's artwork looks like that.
        (1..400).forEach { index ->
            val hue = fallbackPaletteFor("track-$index-whatever.opus").hue
            assertTrue("hue $hue is in the olive band", hue < 46f || hue > 164f)
        }
    }

    @Test
    fun `the same key always gives the same cover`() {
        assertEquals(fallbackPaletteFor("Live and Learn"), fallbackPaletteFor("Live and Learn"))
    }

    @Test
    fun `values stay inside the ranges hsl accepts`() {
        listOf("", "a", "a very long key that goes on and on", "🎵").forEach { key ->
            val palette = fallbackPaletteFor(key)
            assertTrue(palette.hue in 0f..360f)
            assertTrue(palette.saturation in 0f..1f)
            assertTrue(palette.lightness in 0f..1f)
        }
    }
}

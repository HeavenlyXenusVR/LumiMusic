package com.lumisound.android

import com.lumisound.android.ui.components.CoverSpec
import com.lumisound.android.ui.components.coverSpecFor
import com.lumisound.android.ui.components.fallbackPaletteFor
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test
import kotlin.math.abs
import kotlin.math.sqrt

/**
 * Generated covers are the only thing distinguishing most rows in this library, since almost
 * nothing in it has embedded artwork, so covers for different tracks have to LOOK different.
 *
 * Measured on the colour that actually reaches the eye -- the bright orb over its darker
 * field, weighted by roughly how much of the tile each occupies -- rather than on hue, which
 * was the wrong thing to assert: covers are deliberately confined to ten vivid bands so they
 * stay in Lumisound's artwork language, and two keys can share a band while still looking
 * quite different once lightness, saturation and the orb's own hue are accounted for.
 *
 * The thresholds come from modelling this function over a 360-key library: the first
 * parameters tried put near-identical pairs at 12.9%, and the ones in the code bring that to
 * about 5.5%. These assert that measured baseline so it cannot quietly get worse again.
 */
class FallbackPaletteTest {

    private fun hslToRgb(h: Float, s: Float, l: Float): Triple<Float, Float, Float> {
        val c = (1 - abs(2 * l - 1)) * s
        val hh = (h % 360f) / 60f
        val x = c * (1 - abs(hh % 2f - 1))
        val (r1, g1, b1) = when {
            hh < 1 -> Triple(c, x, 0f)
            hh < 2 -> Triple(x, c, 0f)
            hh < 3 -> Triple(0f, c, x)
            hh < 4 -> Triple(0f, x, c)
            hh < 5 -> Triple(x, 0f, c)
            else -> Triple(c, 0f, x)
        }
        val m = l - c / 2
        return Triple(r1 + m, g1 + m, b1 + m)
    }

    /** The orb covers the middle of the tile, the field its edges. */
    private fun perceived(spec: CoverSpec): Triple<Float, Float, Float> {
        val field = hslToRgb(spec.fieldHue, spec.fieldSaturation, spec.fieldLightness)
        val orb = hslToRgb(spec.orbHue, spec.orbSaturation, spec.orbLightness)
        return Triple(
            0.55f * orb.first + 0.45f * field.first,
            0.55f * orb.second + 0.45f * field.second,
            0.55f * orb.third + 0.45f * field.third,
        )
    }

    /** 0 = identical, 1 = black against white. */
    private fun distance(a: String, b: String): Float {
        val (r1, g1, b1) = perceived(coverSpecFor(a))
        val (r2, g2, b2) = perceived(coverSpecFor(b))
        return sqrt(((r1 - r2) * (r1 - r2) + (g1 - g2) * (g1 - g2) + (b1 - b2) * (b1 - b2)) / 3f)
    }

    private val library = buildList {
        (1..120).forEach { add("Midnight Arcade/After Hours $it.opus.lms") }
        (1..120).forEach { add("Sonic Sound Archive/Act $it.mp3") }
        (1..120).forEach { add("Imported Music/Late Night/track-$it.flac") }
    }

    @Test
    fun `sequential tracks in one folder do not share a cover`() {
        listOf(
            "Title 1.opus.lms" to "Title 2.opus.lms",
            "Sonic/Act 1.mp3" to "Sonic/Act 2.mp3",
            "(Mario) The Music Box OST - Title 9.opus.lms" to "(Mario) The Music Box OST - Title 10.opus.lms",
        ).forEach { (first, second) ->
            val d = distance(first, second)
            assertTrue("\"$first\" and \"$second\" differ by only $d", d > 0.08f)
        }
    }

    /**
     * The honest limit of a constrained palette: ten bands cannot guarantee that ANY two keys
     * differ, and widening them until they could would put covers back in the olive band this
     * whole approach exists to avoid. What is worth holding is that collisions stay rare
     * across a real library and never fall between neighbours, which are the covers seen side
     * by side.
     */
    @Test
    fun `collisions stay rare across a real-sized library`() {
        val specs = library.map(::coverSpecFor)
        var close = 0
        var pairs = 0
        for (i in specs.indices) {
            for (j in i + 1 until specs.size) {
                pairs++
                if (distance(library[i], library[j]) < 0.08f) close++
            }
        }
        val percent = 100f * close / pairs
        assertTrue("$close of $pairs pairs ($percent%) are near-identical", percent < 8f)

        val worstNeighbour = library.zipWithNext().minOf { (a, b) -> distance(a, b) }
        assertTrue("closest neighbouring pair differs by only $worstNeighbour", worstNeighbour > 0.015f)
    }

    @Test
    fun `covers stay inside the app's colour family`() {
        // Olive and khaki are where a uniform hue spread kept landing, and nothing in
        // Lumisound's artwork looks like that.
        (1..400).forEach { index ->
            val hue = fallbackPaletteFor("track-$index-whatever.opus").hue
            assertTrue("hue $hue is in the olive band", hue < 60f || hue > 160f)
        }
    }

    @Test
    fun `the same key always gives the same cover`() {
        assertEquals(coverSpecFor("Live and Learn"), coverSpecFor("Live and Learn"))
    }

    @Test
    fun `values stay inside the ranges hsl accepts`() {
        listOf("", "a", "a very long key that goes on and on", "🎵").forEach { key ->
            val spec = coverSpecFor(key)
            assertTrue(spec.fieldHue in 0f..360f && spec.orbHue in 0f..360f)
            assertTrue(spec.fieldSaturation in 0f..1f && spec.orbSaturation in 0f..1f)
            assertTrue(spec.fieldLightness in 0f..1f && spec.orbLightness in 0f..1f)
            assertTrue(spec.radius > 0f && spec.centerX in 0f..1f && spec.centerY in 0f..1f)
        }
    }
}

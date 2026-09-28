package com.lumisound.android

import com.lumisound.android.ui.components.fallbackPaletteFor
import org.junit.Assert.assertTrue
import org.junit.Test
import kotlin.math.abs
import kotlin.math.min

/**
 * Generated artwork is the only thing distinguishing most rows in this library, because
 * almost nothing in it has embedded art. Keys that differ by one character therefore have
 * to land on visibly different colours -- which the first implementation did not do.
 */
class FallbackPaletteTest {

    private fun hueDistance(a: Float, b: Float): Float {
        val d = abs(a - b) % 360f
        return min(d, 360f - d)
    }

    @Test
    fun `adjacent keys are not adjacent colours`() {
        val pairs = listOf(
            "Title 1.opus.lms" to "Title 2.opus.lms",
            "shelf-0" to "shelf-1",
            "Sonic/Act 1.mp3" to "Sonic/Act 2.mp3",
            "a" to "b",
        )
        pairs.forEach { (first, second) ->
            val distance = hueDistance(fallbackPaletteFor(first).hue, fallbackPaletteFor(second).hue)
            assertTrue(
                "\"$first\" and \"$second\" are only $distance degrees apart",
                distance > 25f,
            )
        }
    }

    @Test
    fun `a whole folder of sequential tracks spreads across the wheel`() {
        val hues = (1..12).map { fallbackPaletteFor("(Mario) The Music Box OST - Title $it.opus.lms").hue }
        // Twelve sequential names should cover a decent spread, not cluster in one band.
        assertTrue("hues clustered: $hues", (hues.max() - hues.min()) > 180f)
    }

    @Test
    fun `the same key always gives the same colour`() {
        val once = fallbackPaletteFor("Live and Learn")
        val again = fallbackPaletteFor("Live and Learn")
        assertTrue(once == again)
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

package com.lumisound.android

import com.lumisound.android.ui.aura.AuraColors
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class AuraColorsTest {

    private fun hueOf(argb: Int) = AuraColors.toHsv(argb)[0]

    /**
     * The case averaging gets wrong: a mostly-black cover with a small hot-pink mark. The
     * average is a dull maroon; the aura should be the pink.
     */
    @Test
    fun `a small vivid element outvotes a large dull field`() {
        val black = 0xFF101010.toInt()
        val pink = 0xFFFF2D95.toInt()
        val pixels = IntArray(400) { if (it < 40) pink else black }
        val (primary, _) = AuraColors.extract(pixels)!!
        assertTrue(AuraColors.hueDistance(hueOf(primary), hueOf(pink)) < 12f)
    }

    @Test
    fun `the second colour is a genuinely different hue`() {
        val blue = 0xFF2250FF.toInt()
        val orange = 0xFFFF8A1E.toInt()
        val pixels = IntArray(300) { if (it < 200) blue else orange }
        val (primary, secondary) = AuraColors.extract(pixels)!!
        assertTrue(AuraColors.hueDistance(hueOf(primary), hueOf(blue)) < 12f)
        assertTrue(AuraColors.hueDistance(hueOf(secondary), hueOf(orange)) < 12f)
    }

    @Test
    fun `a single-colour cover still gets two lobes`() {
        val teal = 0xFF14B8A6.toInt()
        val (primary, secondary) = AuraColors.extract(IntArray(100) { teal })!!
        assertTrue(AuraColors.hueDistance(hueOf(primary), hueOf(secondary)) >= 40f)
    }

    @Test
    fun `greys and transparency carry no colour`() {
        assertNull(AuraColors.extract(IntArray(100) { 0xFF808080.toInt() }))
        assertNull(AuraColors.extract(IntArray(100) { 0x00FF0000 }))
    }

    @Test
    fun `hsv round trips`() {
        val argb = AuraColors.fromHsv(210f, 0.7f, 0.8f)
        val hsv = AuraColors.toHsv(argb)
        assertTrue(AuraColors.hueDistance(hsv[0], 210f) < 1.5f)
        assertTrue(kotlin.math.abs(hsv[1] - 0.7f) < 0.02f)
        assertTrue(kotlin.math.abs(hsv[2] - 0.8f) < 0.02f)
    }
}

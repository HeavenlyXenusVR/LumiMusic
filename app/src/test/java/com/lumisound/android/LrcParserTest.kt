package com.lumisound.android

import com.lumisound.android.lyrics.LrcParser
import com.lumisound.android.lyrics.LyricLine
import org.junit.Assert.assertEquals
import org.junit.Test

class LrcParserTest {

    @Test
    fun `parses centisecond timestamps in order`() {
        val lines = LrcParser.parse(
            """
            [00:12.50]Second line
            [00:01.00]First line
            """.trimIndent()
        )
        assertEquals(listOf(LyricLine(1_000, "First line"), LyricLine(12_500, "Second line")), lines)
    }

    @Test
    fun `a line with several timestamps repeats at each`() {
        val lines = LrcParser.parse("[00:10.00][01:10.00]Chorus")
        assertEquals(listOf(10_000L, 70_000L), lines.map { it.timeMs })
        assertEquals(setOf("Chorus"), lines.map { it.text }.toSet())
    }

    @Test
    fun `millisecond and whole-second fractions are both understood`() {
        val lines = LrcParser.parse("[00:02.345]a\n[00:03]b\n[00:04.5]c")
        assertEquals(listOf(2_345L, 3_000L, 4_500L), lines.map { it.timeMs })
    }

    @Test
    fun `metadata tags are skipped and the offset tag shifts every line`() {
        val lines = LrcParser.parse("[ar:Artist]\n[ti:Title]\n[offset:+500]\n[00:05.00]Line")
        assertEquals(listOf(LyricLine(4_500, "Line")), lines)
    }

    @Test
    fun `active line is the last one reached`() {
        val lines = listOf(LyricLine(1_000, "a"), LyricLine(5_000, "b"), LyricLine(9_000, "c"))
        assertEquals(-1, LrcParser.activeIndex(lines, 500))
        assertEquals(0, LrcParser.activeIndex(lines, 1_000))
        assertEquals(1, LrcParser.activeIndex(lines, 8_999))
        assertEquals(2, LrcParser.activeIndex(lines, 60_000))
        assertEquals(-1, LrcParser.activeIndex(emptyList(), 60_000))
    }
}

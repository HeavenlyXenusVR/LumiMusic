package com.lumisound.android

import com.lumisound.android.ui.components.trackSubtitle
import org.junit.Assert.assertEquals
import org.junit.Test

/**
 * These cases are taken from what the real library actually contains -- uploads whose
 * artist tag repeats the title, and per-track folders standing in for an album.
 */
class SubtitleTest {

    @Test
    fun `an artist that repeats the title is dropped`() {
        assertEquals(
            "Unknown artist",
            trackSubtitle(
                title = "( Official Instrumental ) This is Our Big Night",
                artist = "( Official Instrumental ) This is Our Big Night",
                album = "( Official Instrumental ) This is Our Big Night",
            ),
        )
    }

    @Test
    fun `a truncated repeat of the title is still a repeat`() {
        assertEquals(
            "Unknown artist",
            trackSubtitle(title = "Splash Hill Zone Act 1", artist = "Splash Hill Zone", album = null),
        )
    }

    @Test
    fun `real artist and album both survive`() {
        assertEquals(
            "Crush 40 · Sonic Adventure 2",
            trackSubtitle(title = "Live and Learn", artist = "Crush 40", album = "Sonic Adventure 2"),
        )
    }

    @Test
    fun `the server's placeholder artist is not shown`() {
        assertEquals(
            "Sonic Sound Archive",
            trackSubtitle(title = "Windy and Ripply", artist = "Unknown Artist", album = "Sonic Sound Archive"),
        )
    }

    @Test
    fun `a duplicated artist and album collapse to one`() {
        assertEquals(
            "Sonic Sound Archive",
            trackSubtitle(title = "Windy and Ripply", artist = "Sonic Sound Archive", album = "sonic sound archive"),
        )
    }
}

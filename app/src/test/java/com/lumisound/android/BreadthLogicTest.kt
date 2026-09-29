package com.lumisound.android

import com.lumisound.android.bridge.BridgeConfig
import com.lumisound.android.playback.PlaybackUiState
import com.lumisound.android.playback.PodcastProgressTracker
import com.lumisound.android.ui.screens.nowplaying.sleepLabel
import com.lumisound.android.ui.screens.podcasts.stripHtml
import com.lumisound.android.ui.screens.social.parseInstant
import com.lumisound.android.ui.screens.social.timeAgo
import com.lumisound.android.ui.screens.stats.asListeningTime
import com.lumisound.android.ui.screens.stats.heatmapLevels
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test
import java.time.OffsetDateTime

/** The small pure decisions behind the milestone-3 screens. */
class BreadthLogicTest {

    /**
     * The one that matters most: the shared client now fetches thumbnails, podcast artwork
     * and episode audio from other hosts, and a credential reaching any of them would hand
     * out the account's session.
     */
    @Test
    fun `credentials are only for the bridge's own host`() {
        val bridge = "https://bridge.example.com"
        assertTrue(BridgeConfig.isBridgeHost("bridge.example.com", bridge))
        assertTrue(BridgeConfig.isBridgeHost("BRIDGE.example.com", "Bridge.Example.com/"))
        assertFalse(BridgeConfig.isBridgeHost("i.ytimg.com", bridge))
        assertFalse(BridgeConfig.isBridgeHost("bridge.example.com.evil.net", bridge))
        assertFalse(BridgeConfig.isBridgeHost("example.com", bridge))
        assertTrue(BridgeConfig.isBridgeHost("10.0.0.5", "http://10.0.0.5:8000"))
    }

    @Test
    fun `the stream proxy is a shared-key route`() {
        assertTrue(BridgeConfig.isSharedKeyRoute("/api/stream/proxy"))
        assertTrue(BridgeConfig.isSharedKeyRoute("/api/radio"))
        assertFalse(BridgeConfig.isSharedKeyRoute("/api/social/friends"))
        assertFalse(BridgeConfig.isSharedKeyRoute("/user/lyrics"))
    }

    @Test
    fun `heatmap levels split active days into quartiles`() {
        val level = heatmapLevels(listOf(0, 1, 2, 3, 4, 5, 6, 7, 8))
        assertEquals(0, level(0))
        assertEquals(1, level(1))
        assertEquals(4, level(8))
        assertTrue(level(4) in 2..3)
        assertEquals(0, heatmapLevels(emptyList())(5))
    }

    @Test
    fun `listening time reads like a person would say it`() {
        assertEquals("0m", 30L.asListeningTime())
        assertEquals("45m", (45 * 60L).asListeningTime())
        assertEquals("2h 05m", (2 * 3600L + 5 * 60).asListeningTime())
        assertEquals("150h", (150 * 3600L).asListeningTime())
    }

    @Test
    fun `bridge timestamps parse with or without an offset`() {
        assertNotNull(parseInstant("2026-09-29T12:00:00+00:00"))
        assertNotNull(parseInstant("2026-09-29T12:00:00.123456"))
        assertNull(parseInstant("yesterday"))
        assertNull(parseInstant(null))
        val now = OffsetDateTime.parse("2026-09-29T12:00:00Z")
        assertEquals("now", timeAgo("2026-09-29T11:59:40", now))
        assertEquals("5m", timeAgo("2026-09-29T11:55:00", now))
        assertEquals("3h", timeAgo("2026-09-29T09:00:00+00:00", now))
        assertEquals("2d", timeAgo("2026-09-27T12:00:00", now))
        assertEquals("", timeAgo(null, now))
    }

    @Test
    fun `an episode within its last thirty seconds counts as finished`() {
        assertTrue(PodcastProgressTracker.isCompleted(3_580_000, 3_600_000))
        assertFalse(PodcastProgressTracker.isCompleted(1_800_000, 3_600_000))
        assertFalse(PodcastProgressTracker.isCompleted(10_000, 0))
    }

    @Test
    fun `sleep label counts down in whole minutes`() {
        val now = 1_000_000L
        assertNull(sleepLabel(PlaybackUiState(), now))
        assertEquals("Stopping after this track", sleepLabel(PlaybackUiState(sleepAtEndOfTrack = true), now))
        assertEquals("Sleeping in 15 min", sleepLabel(PlaybackUiState(sleepAtMs = now + 15 * 60_000), now))
        assertEquals("Sleeping in 1 min", sleepLabel(PlaybackUiState(sleepAtMs = now + 5_000), now))
    }

    @Test
    fun `episode descriptions lose their markup`() {
        assertEquals("Hello & welcome to the show", stripHtml("<p>Hello &amp; <b>welcome</b></p>\n to the show"))
    }
}

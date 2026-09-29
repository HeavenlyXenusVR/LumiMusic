package com.lumisound.android

import com.lumisound.android.bridge.model.GalleryImageDto
import com.lumisound.android.bridge.model.GallerySyncDto
import com.lumisound.android.gallery.GalleryMapping
import com.lumisound.android.gallery.GallerySettings
import com.lumisound.android.gallery.GalleryTransition
import com.lumisound.android.ui.gallery.photoLayer
import com.lumisound.android.ui.screens.settings.intervalLabel
import com.lumisound.android.ui.screens.settings.syncedLabel
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class GalleryBackgroundTest {

    private val eps = 1e-4f

    // --- Lumisound's settings -----------------------------------------------------------

    @Test
    fun `lumisound's raw animation names all map`() {
        val raw = listOf("Fade", "Slide Left", "Slide Right", "Slide Up", "Slide Down", "Zoom In", "Zoom Out", "Zoom Blur", "Flip", "Twist", "Blur In", "Cut")
        assertEquals(GalleryTransition.entries.toList(), raw.map { GalleryTransition.fromLumisound(it) })
    }

    @Test
    fun `the bridge's lowercase default and the legacy none are understood`() {
        assertEquals(GalleryTransition.Fade, GalleryTransition.fromLumisound("fade"))
        assertEquals(GalleryTransition.Cut, GalleryTransition.fromLumisound("none"))
        assertEquals(GalleryTransition.SlideLeft, GalleryTransition.fromLumisound("slide-left"))
        assertNull(GalleryTransition.fromLumisound("wobble"))
        assertNull(GalleryTransition.fromLumisound(null))
    }

    @Test
    fun `a photo background on the iPhone is applied here`() {
        val sync = GallerySyncDto(
            enabled = true,
            opacity = 0.6,
            blurRadius = 22.0,
            shuffleIntervalSeconds = 60.0,
            animation = "Zoom Out",
            extraSettingsJson = """{"galleryBackground_source":"photos","bgService.kenBurnsEnabled":true,"other":3}""",
        )
        val settings = GalleryMapping.fromLumisound(sync)
        assertEquals(GallerySettings(true, 0.6f, 22f, 60, GalleryTransition.ZoomOut, kenBurns = true), settings)
    }

    @Test
    fun `sonic wallpaper or reactive aura on the iPhone means no photos here`() {
        listOf("sonic", "reactive").forEach { source ->
            val sync = GallerySyncDto(enabled = true, extraSettingsJson = """{"galleryBackground_source":"$source"}""")
            assertFalse(GalleryMapping.fromLumisound(sync).enabled)
        }
    }

    @Test
    fun `unset fields keep the fallback and out-of-range values are clamped`() {
        val fallback = GallerySettings(enabled = true, intervalSeconds = 120, transition = GalleryTransition.Twist)
        val settings = GalleryMapping.fromLumisound(GallerySyncDto(opacity = 4.0, blurRadius = -3.0, extraSettingsJson = "not json"), fallback)
        assertTrue(settings.enabled)
        assertEquals(1f, settings.opacity, eps)
        assertEquals(0f, settings.blurRadius, eps)
        assertEquals(120, settings.intervalSeconds)
        assertEquals(GalleryTransition.Twist, settings.transition)
        assertEquals(5, GalleryMapping.fromLumisound(GallerySyncDto(shuffleIntervalSeconds = 1.0)).intervalSeconds)
    }

    // --- Photos -------------------------------------------------------------------------

    @Test
    fun `photos come in lumisound's order with absolute bridge urls`() {
        val images = listOf(
            GalleryImageDto(id = "c", displayOrder = 1, uploadedAt = "2026-09-01T10:00:00", url = "/user/gallery/images/c"),
            GalleryImageDto(id = "a", displayOrder = 0, uploadedAt = "2026-08-01T10:00:00", url = "/user/gallery/images/a"),
            GalleryImageDto(id = "b", displayOrder = 0, uploadedAt = "2026-09-20T10:00:00", url = null),
        )
        val photos = GalleryMapping.photos(images, "https://bridge.example.com/")
        assertEquals(listOf("b", "a", "c"), photos.map { it.id })
        assertEquals("https://bridge.example.com/user/gallery/images/b", photos[0].url)
        assertEquals("https://bridge.example.com/user/gallery/images/a", photos[1].url)
    }

    @Test
    fun `a url that would leave the bridge host is dropped`() {
        val images = listOf(
            GalleryImageDto(id = "x", url = "//attacker.example/steal"),
            GalleryImageDto(id = "y", url = "https://attacker.example/steal"),
            GalleryImageDto(id = "", url = "/user/gallery/images/z"),
        )
        val photos = GalleryMapping.photos(images, "https://bridge.example.com")
        // An absolute url is replaced by the bridge path for that id; a protocol-relative one is dropped.
        assertEquals(listOf("https://bridge.example.com/user/gallery/images/y"), photos.map { it.url })
    }

    // --- Transitions --------------------------------------------------------------------

    @Test
    fun `every transition ends with only the new photo showing`() {
        GalleryTransition.entries.forEach { t ->
            val incoming = photoLayer(t, 1f, incoming = true)
            val outgoing = photoLayer(t, 1f, incoming = false)
            assertEquals("$t incoming alpha", 1f, incoming.alpha, eps)
            assertEquals("$t incoming x", 0f, incoming.translateX, eps)
            assertEquals("$t incoming y", 0f, incoming.translateY, eps)
            assertEquals("$t incoming scale", 1f, incoming.scale, eps)
            assertEquals("$t incoming turn", 0f, incoming.rotationY + incoming.rotationZ, eps)
            assertEquals("$t incoming blur", 0f, incoming.extraBlur, eps)
            val gone = outgoing.alpha == 0f || kotlin.math.abs(outgoing.translateX) >= 1f || kotlin.math.abs(outgoing.translateY) >= 1f
            assertTrue("$t outgoing still visible", gone)
        }
    }

    @Test
    fun `every transition starts with only the old photo showing`() {
        GalleryTransition.entries.filter { it != GalleryTransition.Cut }.forEach { t ->
            val incoming = photoLayer(t, 0f, incoming = true)
            val outgoing = photoLayer(t, 0f, incoming = false)
            val hidden = incoming.alpha == 0f || kotlin.math.abs(incoming.translateX) >= 1f || kotlin.math.abs(incoming.translateY) >= 1f
            assertTrue("$t incoming visible at start", hidden)
            assertEquals("$t outgoing alpha", 1f, outgoing.alpha, eps)
        }
    }

    // --- Labels -------------------------------------------------------------------------

    @Test
    fun `labels read naturally`() {
        assertEquals("30s", intervalLabel(30))
        assertEquals("2 min", intervalLabel(120))
        assertEquals("synced just now", syncedLabel(1_000_000, 1_020_000))
        assertEquals("synced 5 min ago", syncedLabel(0, 5 * 60_000L))
        assertEquals("synced 3 h ago", syncedLabel(0, 3 * 3_600_000L))
        assertEquals("synced 2 days ago", syncedLabel(0, 49 * 3_600_000L))
    }
}

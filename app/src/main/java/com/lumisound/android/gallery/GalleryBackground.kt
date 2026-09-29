package com.lumisound.android.gallery

import com.google.gson.JsonParser
import com.lumisound.android.bridge.BridgeConfig
import com.lumisound.android.bridge.model.GalleryImageDto
import com.lumisound.android.bridge.model.GallerySyncDto

/**
 * How one photo gives way to the next. Mirrors Lumisound's `BackgroundAnimation`, whose
 * raw values ("Fade", "Slide Left", "Blur In", "Cut", ...) are what `bg_animation` holds.
 */
enum class GalleryTransition(val label: String) {
    Fade("Fade"),
    SlideLeft("Slide left"),
    SlideRight("Slide right"),
    SlideUp("Slide up"),
    SlideDown("Slide down"),
    ZoomIn("Zoom in"),
    ZoomOut("Zoom out"),
    ZoomBlur("Zoom blur"),
    Flip("Flip"),
    Twist("Twist"),
    BlurIn("Blur in"),
    Cut("Cut");

    companion object {
        /**
         * Reads Lumisound's raw value. The bridge's own default is lowercase "fade" and
         * older builds wrote "none" for Cut, so matching ignores case, spaces and hyphens.
         */
        fun fromLumisound(raw: String?): GalleryTransition? {
            val key = raw?.lowercase()?.filter { it.isLetter() } ?: return null
            return when (key) {
                "fade" -> Fade
                "slideleft" -> SlideLeft
                "slideright" -> SlideRight
                "slideup" -> SlideUp
                "slidedown" -> SlideDown
                "zoomin" -> ZoomIn
                "zoomout" -> ZoomOut
                "zoomblur" -> ZoomBlur
                "flip" -> Flip
                "twist" -> Twist
                "blurin", "blur" -> BlurIn
                "cut", "none" -> Cut
                else -> null
            }
        }
    }
}

/** Everything that shapes the gallery background, in Lumisound's units. */
data class GallerySettings(
    val enabled: Boolean = false,
    /** How strongly the photo shows over the page, 0.05..1. */
    val opacity: Float = DEFAULT_OPACITY,
    /** Gaussian blur radius in dp, 0..40 -- Lumisound's slider range. */
    val blurRadius: Float = DEFAULT_BLUR,
    /** Seconds each photo stays up before the next. */
    val intervalSeconds: Int = DEFAULT_INTERVAL,
    val transition: GalleryTransition = GalleryTransition.Fade,
    /** A slow zoom-and-drift while each photo is up. */
    val kenBurns: Boolean = false,
) {
    fun normalised() = copy(
        opacity = opacity.coerceIn(MIN_OPACITY, 1f),
        blurRadius = blurRadius.coerceIn(0f, MAX_BLUR),
        intervalSeconds = intervalSeconds.coerceIn(MIN_INTERVAL, MAX_INTERVAL),
    )

    companion object {
        const val DEFAULT_OPACITY = 0.35f
        const val DEFAULT_BLUR = 16f
        const val DEFAULT_INTERVAL = 30
        const val MIN_OPACITY = 0.05f
        const val MAX_BLUR = 40f
        const val MIN_INTERVAL = 5
        const val MAX_INTERVAL = 600

        /** Lumisound's interval presets, so both apps offer the same choices. */
        val INTERVAL_PRESETS = listOf(5, 10, 15, 30, 60, 120, 300)
    }
}

/** One photo, ready to load: [url] is absolute and on the bridge host. */
data class GalleryPhoto(val id: String, val url: String)

object GalleryMapping {

    /** Lumisound's UserDefaults keys inside `extra_settings_json`. */
    const val SOURCE_KEY = "galleryBackground_source"
    const val KEN_BURNS_KEY = "bgService.kenBurnsEnabled"

    /**
     * Lumisound's settings, as this app applies them.
     *
     * The background is on only when Lumisound's is on and showing photos. Its other two
     * sources, Sonic Wallpaper and Reactive Aura, are generated per device and have no
     * photos to share; the aura here already fills that role. Fields the account has never
     * set keep [fallback]'s value.
     */
    fun fromLumisound(sync: GallerySyncDto, fallback: GallerySettings = GallerySettings()): GallerySettings {
        val extras = parseExtras(sync.extraSettingsJson)
        val source = extras[SOURCE_KEY] as? String
        val showsPhotos = source == null || source == "photos"
        return GallerySettings(
            enabled = (sync.enabled ?: fallback.enabled) && showsPhotos,
            opacity = sync.opacity?.toFloat() ?: fallback.opacity,
            blurRadius = sync.blurRadius?.toFloat() ?: fallback.blurRadius,
            intervalSeconds = sync.shuffleIntervalSeconds?.let { Math.round(it).toInt() } ?: fallback.intervalSeconds,
            transition = GalleryTransition.fromLumisound(sync.animation) ?: fallback.transition,
            kenBurns = extras[KEN_BURNS_KEY] as? Boolean ?: fallback.kenBurns,
        ).normalised()
    }

    /**
     * The photos in the order Lumisound shows them: by display order, and newest first
     * within one order, which is how the bridge already lists them. Entries without an id
     * or url are dropped, as is any url that would leave the bridge host: the session
     * token rides on every request to the bridge, and nowhere else.
     */
    fun photos(images: List<GalleryImageDto>, baseUrl: String): List<GalleryPhoto> {
        val base = BridgeConfig.normalise(baseUrl)
        return images
            .filter { it.id.isNotBlank() }
            .sortedWith(compareBy<GalleryImageDto> { it.displayOrder }.thenByDescending { it.uploadedAt.orEmpty() })
            .mapNotNull { image ->
                val path = image.url?.takeIf { it.startsWith("/") } ?: "/user/gallery/images/${image.id}"
                if (path.startsWith("//")) return@mapNotNull null
                GalleryPhoto(image.id, base + path)
            }
            .distinctBy { it.id }
    }

    /** `extra_settings_json` as a flat map of booleans, numbers and strings. */
    fun parseExtras(json: String?): Map<String, Any> {
        if (json.isNullOrBlank()) return emptyMap()
        return try {
            val obj = JsonParser.parseString(json).takeIf { it.isJsonObject }?.asJsonObject ?: return emptyMap()
            buildMap {
                for ((key, value) in obj.entrySet()) {
                    if (!value.isJsonPrimitive) continue
                    val primitive = value.asJsonPrimitive
                    put(
                        key,
                        when {
                            primitive.isBoolean -> primitive.asBoolean
                            primitive.isNumber -> primitive.asDouble
                            else -> primitive.asString
                        },
                    )
                }
            }
        } catch (e: Exception) {
            emptyMap()
        }
    }
}

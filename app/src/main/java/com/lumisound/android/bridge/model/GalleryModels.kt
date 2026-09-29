package com.lumisound.android.bridge.model

import com.google.gson.annotations.SerializedName

/**
 * One photo in the account's cloud gallery, as `GET /user/gallery/images` lists it. [url]
 * is bridge-relative (`/user/gallery/images/{id}`) and needs the session token to fetch.
 * Lumisound on iPhone uploads these when photos are added to its Gallery Background.
 */
data class GalleryImageDto(
    val id: String = "",
    val filename: String? = null,
    @SerializedName("display_order") val displayOrder: Int = 0,
    @SerializedName("uploaded_at") val uploadedAt: String? = null,
    val url: String? = null,
)

/**
 * The gallery-background slice of `GET /user/sync`. The snapshot carries the whole
 * account (favorites, playlists, history); Gson skips everything not named here.
 *
 * The first five are dedicated columns Lumisound writes from its Background settings.
 * The photo-vs-generated source and Ken Burns motion live in [extraSettingsJson], a JSON
 * object keyed by Lumisound's UserDefaults names.
 */
data class GallerySyncDto(
    @SerializedName("bg_enabled") val enabled: Boolean? = null,
    @SerializedName("bg_opacity") val opacity: Double? = null,
    @SerializedName("bg_blur_radius") val blurRadius: Double? = null,
    @SerializedName("bg_shuffle_interval") val shuffleIntervalSeconds: Double? = null,
    @SerializedName("bg_animation") val animation: String? = null,
    @SerializedName("extra_settings_json") val extraSettingsJson: String? = null,
)

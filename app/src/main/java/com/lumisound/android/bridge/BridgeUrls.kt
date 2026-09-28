package com.lumisound.android.bridge

import android.net.Uri

/**
 * URL builders for the two bridge resources that are fetched by something other
 * than Retrofit -- the audio stream (ExoPlayer) and artwork (Coil).
 */
object BridgeUrls {

    /**
     * `GET /user/music/stream?path=...`. Auth rides on the Authorization header
     * (the shared OkHttp client adds it), which works here because ExoPlayer's
     * OkHttp data source reapplies request headers to every byte-range request
     * it makes -- unlike AVFoundation on iOS, which drops them after the opening
     * request and forced Lumisound to move the credential into a query-string
     * stream ticket instead.
     *
     * [lockedMarker] is appended for a `.lms` track purely so the data source can
     * recognise one from the URI alone; the server ignores unknown query params.
     */
    fun stream(baseUrl: String, serverPath: String, isLocked: Boolean, quality: String? = null): String =
        Uri.parse(BridgeConfig.normalise(baseUrl)).buildUpon()
            .appendEncodedPath("user/music/stream")
            .appendQueryParameter("path", serverPath)
            .apply {
                if (quality != null) appendQueryParameter("quality", quality)
                if (isLocked) appendQueryParameter(LOCKED_MARKER, "1")
            }
            .build()
            .toString()

    fun artwork(baseUrl: String, serverPath: String): String =
        Uri.parse(BridgeConfig.normalise(baseUrl)).buildUpon()
            .appendEncodedPath("user/music/artwork")
            .appendQueryParameter("path", serverPath)
            .build()
            .toString()

    fun avatar(baseUrl: String, userId: String): String =
        Uri.parse(BridgeConfig.normalise(baseUrl)).buildUpon()
            .appendEncodedPath("user/avatar")
            .appendPath(userId)
            .build()
            .toString()

    const val LOCKED_MARKER = "lms"
    const val LOCKED_MARKER_VALUE = "1"
}

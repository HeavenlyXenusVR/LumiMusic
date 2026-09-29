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

    /**
     * `GET /api/stream/proxy` -- a YouTube or SoundCloud track re-streamed through the
     * bridge. The bridge has to be in the path: googlevideo URLs are bound to the IP that
     * extracted them, so handing the player the raw CDN URL from `/api/stream` 403s.
     *
     * No ticket in the URL. That exists for AVPlayer, which drops headers after the first
     * request; ExoPlayer resends them on every range request, so the interceptor's
     * `X-Account-Token` authenticates each one.
     *
     * [pageUrl] is required by the bridge for anything that is not YouTube.
     */
    fun streamProxy(baseUrl: String, id: String, source: String, pageUrl: String?): String =
        Uri.parse(BridgeConfig.normalise(baseUrl)).buildUpon()
            .appendEncodedPath("api/stream/proxy")
            .appendQueryParameter("id", id)
            .appendQueryParameter("source", source)
            .apply { if (source != "youtube" && !pageUrl.isNullOrBlank()) appendQueryParameter("url", pageUrl) }
            // m4a rather than the bridge's download formats: AAC in MP4 is what every
            // Android decoder handles, and the bridge narrows live playback to it anyway.
            .appendQueryParameter("format", "m4a")
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

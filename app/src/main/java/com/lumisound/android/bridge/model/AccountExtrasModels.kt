package com.lumisound.android.bridge.model

import com.google.gson.annotations.SerializedName

/**
 * `GET /user/lyrics` -- the JWT-gated twin of `/api/lyrics`, reading the same shared
 * cache. That cache is also where Aria's Whisper transcriptions and user corrections
 * land, which no public lyrics database has.
 */
data class LyricsDto(
    val title: String? = null,
    val artist: String? = null,
    @SerializedName("synced_lyrics") val syncedLyrics: String? = null,
    @SerializedName("plain_lyrics") val plainLyrics: String? = null,
    val instrumental: Boolean = false,
    val source: String? = null,
)

/** One row of the in-app inbox (`GET /user/notifications`). */
data class NotificationDto(
    val id: String = "",
    val type: String = "",
    val title: String? = null,
    val body: String? = null,
    @SerializedName("created_at") val createdAt: String? = null,
    @SerializedName("read_at") val readAt: String? = null,
) {
    val isUnread: Boolean get() = readAt == null
}

/** `GET /user/scrobble`: which scrobbling services this account has linked. */
data class ScrobbleLinksDto(
    @SerializedName("lastfm_linked") val lastfmLinked: Boolean = false,
    @SerializedName("lastfm_username") val lastfmUsername: String? = null,
    @SerializedName("listenbrainz_linked") val listenbrainzLinked: Boolean = false,
    @SerializedName("librefm_linked") val librefmLinked: Boolean = false,
    @SerializedName("librefm_username") val librefmUsername: String? = null,
    val enabled: Boolean = true,
)

/**
 * `PUT /user/scrobble`. The link fields are preserve-if-null server-side, but `enabled` is
 * NOT: a null there is stored as true. So every write sends the current `enabled` value,
 * or saving a token would silently switch scrobbling back on.
 */
data class ScrobbleUpdateRequest(
    @SerializedName("listenbrainz_token") val listenbrainzToken: String? = null,
    val enabled: Boolean,
)

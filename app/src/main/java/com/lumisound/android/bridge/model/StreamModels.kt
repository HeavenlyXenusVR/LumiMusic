package com.lumisound.android.bridge.model

import com.google.gson.annotations.SerializedName

/**
 * The bridge's `StreamTrack` -- what `_parse_track` builds from a yt-dlp entry, and the
 * shape every "here is something to stream" route shares: search, radio, discover mix,
 * on-this-day and Aria's daily pick all return this.
 *
 * `youtube_url` is the canonical page URL for any source, SoundCloud included; it is what
 * a play is logged against (`track_url`) and what `/api/stream/proxy` needs as `url=` for
 * anything that is not YouTube.
 */
data class StreamTrackDto(
    val id: String = "",
    val title: String = "",
    val artist: String = "",
    @SerializedName("duration_seconds") val durationSeconds: Int = 0,
    @SerializedName("thumbnail_url") val thumbnailUrl: String? = null,
    val source: String = "youtube",
    @SerializedName("youtube_url") val youtubeUrl: String? = null,
    @SerializedName("is_topic_channel") val isTopicChannel: Boolean = false,
)

/** `GET /api/search/trending` and `/api/search/suggestions`: past queries by popularity. */
data class SearchQueryDto(
    val query: String = "",
    val count: Int = 0,
)

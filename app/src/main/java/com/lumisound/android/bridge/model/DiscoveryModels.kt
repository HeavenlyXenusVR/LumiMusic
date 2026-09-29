package com.lumisound.android.bridge.model

import com.google.gson.annotations.SerializedName

/**
 * `GET /user/music/weekly-mix`. Built server-side from the account's own cloud uploads,
 * so every track here is also a `/user/music` track -- `relative_path` is the same value
 * the listing calls `server_path`.
 */
data class WeeklyMixResponse(
    val tracks: List<WeeklyMixTrackDto> = emptyList(),
    @SerializedName("generated_at") val generatedAt: String? = null,
)

data class WeeklyMixTrackDto(
    @SerializedName("metadata_id") val metadataId: String? = null,
    val title: String = "",
    val artist: String = "",
    val album: String = "",
    val bpm: Double? = null,
    @SerializedName("musical_key") val musicalKey: String? = null,
    @SerializedName("relative_path") val relativePath: String = "",
    @SerializedName("has_artwork") val hasArtwork: Boolean = false,
)

/** `GET /user/aria/daily-pick`: one AI-picked track a day, cached per account per day. */
data class DailyPickResponse(
    val pick: StreamTrackDto? = null,
    val reason: String? = null,
)

/** One year's worth of `GET /user/on-this-day`. */
data class OnThisDayGroupDto(
    @SerializedName("years_ago") val yearsAgo: Int = 0,
    val year: Int = 0,
    val tracks: List<StreamTrackDto> = emptyList(),
)

/** `GET /user/social/twin`: the one opted-in listener whose top artists overlap most. */
data class TwinResponse(
    val twin: TwinDto? = null,
    val reason: String? = null,
)

data class TwinDto(
    val username: String = "",
    @SerializedName("display_name") val displayName: String? = null,
    @SerializedName("avatar_url") val avatarUrl: String? = null,
    val similarity: Int = 0,
    @SerializedName("shared_artists") val sharedArtists: List<String> = emptyList(),
)

/**
 * `GET /social/discover`: the most-played title/artist pairs among listeners who share
 * their activity. Carries no stream id -- the server only ever stored the title and
 * artist -- so playing one means searching for it.
 */
data class CommunityTrendingResponse(
    val tracks: List<CommunityTrackDto> = emptyList(),
)

data class CommunityTrackDto(
    val title: String = "",
    val artist: String? = null,
    @SerializedName("play_count") val playCount: Int = 0,
    @SerializedName("listener_count") val listenerCount: Int = 0,
)

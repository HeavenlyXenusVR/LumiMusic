package com.lumisound.android.bridge.model

import com.google.gson.annotations.SerializedName

/** `GET /user/stats`: lifetime totals plus the top five artists and tracks. */
data class LifetimeStatsDto(
    @SerializedName("total_plays") val totalPlays: Int = 0,
    @SerializedName("total_listen_seconds") val totalListenSeconds: Long = 0,
    @SerializedName("top_artists") val topArtists: List<TopArtistDto> = emptyList(),
    @SerializedName("top_tracks") val topTracks: List<TopTrackDto> = emptyList(),
)

data class TopArtistDto(
    val artist: String = "",
    @SerializedName("play_count") val playCount: Int = 0,
    @SerializedName("listen_seconds") val listenSeconds: Long? = null,
)

data class TopTrackDto(
    val title: String = "",
    val artist: String? = null,
    @SerializedName("play_count") val playCount: Int = 0,
)

/**
 * One calendar day of listening, as `/user/stats/weekly`, `/user/stats/heatmap` and the
 * review endpoints all report it. Days with no plays are simply absent, not zero rows.
 */
data class DayStatDto(
    val date: String = "",
    val plays: Int = 0,
    @SerializedName("listen_seconds") val listenSeconds: Long = 0,
)

data class MonthStatDto(
    val month: Int = 0,
    val plays: Int = 0,
    @SerializedName("listen_seconds") val listenSeconds: Long = 0,
)

/**
 * `GET /user/stats/year-in-review` and `/user/stats/month-in-review` share one shape; the
 * year variant fills [byMonth], the month variant [byDay] and [month].
 */
data class ReviewDto(
    val year: Int = 0,
    val month: Int? = null,
    @SerializedName("total_plays") val totalPlays: Int = 0,
    @SerializedName("total_listen_seconds") val totalListenSeconds: Long = 0,
    @SerializedName("distinct_artists") val distinctArtists: Int = 0,
    @SerializedName("distinct_tracks") val distinctTracks: Int = 0,
    @SerializedName("average_bpm") val averageBpm: Double? = null,
    @SerializedName("top_artists") val topArtists: List<TopArtistDto> = emptyList(),
    @SerializedName("top_tracks") val topTracks: List<TopTrackDto> = emptyList(),
    @SerializedName("by_month") val byMonth: List<MonthStatDto> = emptyList(),
    @SerializedName("by_day") val byDay: List<DayStatDto> = emptyList(),
    @SerializedName("peak_day") val peakDay: DayStatDto? = null,
)

/** `GET /user/achievements`: computed on the fly from play history, no table behind it. */
data class AchievementsDto(
    @SerializedName("total_plays") val totalPlays: Int = 0,
    @SerializedName("total_listen_seconds") val totalListenSeconds: Long = 0,
    @SerializedName("current_streak_days") val currentStreakDays: Int = 0,
    @SerializedName("longest_streak_days") val longestStreakDays: Int = 0,
    val badges: List<String> = emptyList(),
)

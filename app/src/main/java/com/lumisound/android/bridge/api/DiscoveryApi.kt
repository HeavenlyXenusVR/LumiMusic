package com.lumisound.android.bridge.api

import com.lumisound.android.bridge.model.AchievementsDto
import com.lumisound.android.bridge.model.CommunityTrendingResponse
import com.lumisound.android.bridge.model.DailyPickResponse
import com.lumisound.android.bridge.model.DayStatDto
import com.lumisound.android.bridge.model.LifetimeStatsDto
import com.lumisound.android.bridge.model.LyricsDto
import com.lumisound.android.bridge.model.NotificationDto
import com.lumisound.android.bridge.model.OnThisDayGroupDto
import com.lumisound.android.bridge.model.ReviewDto
import com.lumisound.android.bridge.model.ScrobbleLinksDto
import com.lumisound.android.bridge.model.ScrobbleUpdateRequest
import com.lumisound.android.bridge.model.StreamTrackDto
import com.lumisound.android.bridge.model.TwinResponse
import com.lumisound.android.bridge.model.WeeklyMixResponse
import retrofit2.http.Body
import retrofit2.http.GET
import retrofit2.http.POST
import retrofit2.http.PUT
import retrofit2.http.Path
import retrofit2.http.Query

/**
 * Everything the bridge derives from an account's own listening: the Home dashboard's
 * mixes and picks, stats and recaps, achievements, lyrics, the inbox and scrobbling.
 *
 * All of it is computed from the same `ios_play_history` rows Lumisound writes, so none
 * of it needs anything new from this app beyond logging plays -- which it already does.
 */
interface DiscoveryApi {
    @GET("user/music/weekly-mix")
    suspend fun weeklyMix(@Query("force_regenerate") regenerate: Boolean = false): WeeklyMixResponse

    /** Seeded by the account's most-played artists, excluding anything already owned. */
    @GET("user/discover-mix")
    suspend fun discoverMix(@Query("limit") limit: Int = 20): List<StreamTrackDto>

    @GET("user/aria/daily-pick")
    suspend fun dailyPick(): DailyPickResponse

    @GET("user/on-this-day")
    suspend fun onThisDay(): List<OnThisDayGroupDto>

    @GET("user/social/twin")
    suspend fun listeningTwin(): TwinResponse

    @GET("user/social/twin/mix")
    suspend fun twinMix(@Query("limit") limit: Int = 20): List<StreamTrackDto>

    @GET("social/discover")
    suspend fun communityTrending(
        @Query("days") days: Int = 7,
        @Query("limit") limit: Int = 20,
    ): CommunityTrendingResponse

    @GET("user/stats")
    suspend fun lifetimeStats(): LifetimeStatsDto

    @GET("user/stats/weekly")
    suspend fun weeklyStats(): List<DayStatDto>

    @GET("user/stats/heatmap")
    suspend fun heatmap(@Query("days") days: Int = 365): List<DayStatDto>

    @GET("user/stats/year-in-review")
    suspend fun yearInReview(@Query("year") year: Int? = null): ReviewDto

    @GET("user/stats/month-in-review")
    suspend fun monthInReview(
        @Query("year") year: Int? = null,
        @Query("month") month: Int? = null,
    ): ReviewDto

    /**
     * [tzOffsetMinutes] shifts the server's day boundaries onto the device's own, without
     * which a streak kept every local evening can break across UTC midnight.
     */
    @GET("user/achievements")
    suspend fun achievements(@Query("tz_offset_minutes") tzOffsetMinutes: Int): AchievementsDto

    @GET("user/lyrics")
    suspend fun lyrics(
        @Query("title") title: String,
        @Query("artist") artist: String = "",
        @Query("duration") durationSeconds: Int? = null,
    ): LyricsDto

    @GET("user/notifications")
    suspend fun notifications(
        @Query("limit") limit: Int = 50,
        @Query("unread_only") unreadOnly: Boolean = false,
    ): List<NotificationDto>

    @POST("user/notifications/{id}/read")
    suspend fun markNotificationRead(@Path("id") id: String)

    @POST("user/notifications/read-all")
    suspend fun markAllNotificationsRead()

    @GET("user/scrobble")
    suspend fun scrobbleLinks(): ScrobbleLinksDto

    /** Answers `{"status": "ok"}`, not the links; re-read them afterwards. */
    @PUT("user/scrobble")
    suspend fun updateScrobble(@Body body: ScrobbleUpdateRequest)
}

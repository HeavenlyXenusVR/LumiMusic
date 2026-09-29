package com.lumisound.android.bridge.api

import com.lumisound.android.bridge.model.SearchQueryDto
import com.lumisound.android.bridge.model.StreamTrackDto
import retrofit2.http.GET
import retrofit2.http.Query

/**
 * The bridge's yt-dlp-backed catalogue: YouTube and SoundCloud search, radio from a
 * seed track, and what other people have been searching for.
 *
 * These are the legacy `check_auth()` routes, so the auth interceptor sends the shared
 * bridge key on them rather than the session token -- and adds the session token as
 * `X-Account-Token` alongside it, which is how the bridge picks up the account's own
 * YouTube API key and cookies for a search. Playback itself is not a Retrofit call; see
 * `BridgeUrls.streamProxy`.
 */
interface StreamingApi {
    @GET("api/search")
    suspend fun search(
        @Query("q") query: String,
        @Query("source") source: String = "youtube",
        @Query("limit") limit: Int = 25,
    ): List<StreamTrackDto>

    /** YouTube's own auto-generated "Mix" for a seed video, seed excluded. YouTube only. */
    @GET("api/radio")
    suspend fun radio(
        @Query("id") seedId: String,
        @Query("source") source: String = "youtube",
        @Query("limit") limit: Int = 25,
    ): List<StreamTrackDto>

    @GET("api/search/trending")
    suspend fun trendingQueries(
        @Query("limit") limit: Int = 12,
        @Query("days") days: Int = 7,
    ): List<SearchQueryDto>

    @GET("api/search/suggestions")
    suspend fun suggestions(
        @Query("q") prefix: String,
        @Query("limit") limit: Int = 8,
    ): List<SearchQueryDto>
}

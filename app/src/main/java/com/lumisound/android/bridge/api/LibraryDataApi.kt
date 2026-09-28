package com.lumisound.android.bridge.api

import com.lumisound.android.bridge.model.AddFavoriteRequest
import com.lumisound.android.bridge.model.CreatePlaylistRequest
import com.lumisound.android.bridge.model.PlaylistTrackRequest
import com.lumisound.android.bridge.model.UpdatePlaylistRequest
import com.lumisound.android.bridge.model.FavoriteDto
import com.lumisound.android.bridge.model.HistoryEntryDto
import com.lumisound.android.bridge.model.LogPlayRequest
import com.lumisound.android.bridge.model.PlaylistDto
import com.lumisound.android.bridge.model.UpdateSettingsRequest
import com.lumisound.android.bridge.model.UserSettingsDto
import retrofit2.http.Body
import retrofit2.http.DELETE
import retrofit2.http.GET
import retrofit2.http.POST
import retrofit2.http.PUT
import retrofit2.http.Path
import retrofit2.http.Query

/**
 * Account-scoped library data: favorites, playlists, play history and the small
 * shared settings row. Everything here is read/written by Lumisound too, so a
 * favorite added on Android appears on iOS and vice versa.
 *
 * Note what is NOT here: `POST /user/sync`. That endpoint unconditionally
 * DELETEs the caller's favorites and playlists before checking whether the
 * request supplied any, so a settings-only push through it silently wipes both.
 * This app uses the individual endpoints below instead, which have no such
 * asymmetry.
 */
interface LibraryDataApi {
    @GET("user/favorites")
    suspend fun favorites(): List<FavoriteDto>

    @POST("user/favorites")
    suspend fun addFavorite(@Body body: AddFavoriteRequest)

    @DELETE("user/favorites/{songId}")
    suspend fun removeFavorite(@Path("songId") songId: String)

    @GET("user/playlists")
    suspend fun playlists(): List<PlaylistDto>

    @GET("user/playlists/{id}")
    suspend fun playlist(@Path("id") id: String): PlaylistDto

    @POST("user/playlists")
    suspend fun createPlaylist(@Body body: CreatePlaylistRequest): PlaylistDto

    @PUT("user/playlists/{id}")
    suspend fun updatePlaylist(@Path("id") id: String, @Body body: UpdatePlaylistRequest): PlaylistDto

    @DELETE("user/playlists/{id}")
    suspend fun deletePlaylist(@Path("id") id: String)

    /** Owner or an 'editor' collaborator only; a viewer gets a 403. */
    @POST("user/playlists/{id}/tracks")
    suspend fun addPlaylistTrack(@Path("id") id: String, @Body body: PlaylistTrackRequest)

    @DELETE("user/playlists/{id}/tracks/{trackId}")
    suspend fun removePlaylistTrack(@Path("id") id: String, @Path("trackId") trackId: String)

    /** Returns a bare array, newest first -- capped at 200 server-side. */
    @GET("user/history")
    suspend fun history(@Query("limit") limit: Int = 200): List<HistoryEntryDto>

    @POST("user/history")
    suspend fun logPlay(@Body body: LogPlayRequest)

    @GET("user/settings")
    suspend fun settings(): UserSettingsDto

    @PUT("user/settings")
    suspend fun updateSettings(@Body body: UpdateSettingsRequest): UserSettingsDto
}

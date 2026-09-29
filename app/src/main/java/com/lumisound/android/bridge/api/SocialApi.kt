package com.lumisound.android.bridge.api

import com.lumisound.android.bridge.model.CompatibilityDto
import com.lumisound.android.bridge.model.FriendActivityResponse
import com.lumisound.android.bridge.model.FriendRequestCreate
import com.lumisound.android.bridge.model.FriendRequestsResponse
import com.lumisound.android.bridge.model.FriendsResponse
import com.lumisound.android.bridge.model.PresenceResponse
import com.lumisound.android.bridge.model.PresenceUpdate
import com.lumisound.android.bridge.model.ProfileDto
import com.lumisound.android.bridge.model.UserSearchResponse
import retrofit2.http.Body
import retrofit2.http.DELETE
import retrofit2.http.GET
import retrofit2.http.POST
import retrofit2.http.Path
import retrofit2.http.Query

/**
 * Friends, presence and profiles -- the `/api/social/` family. JWT-gated throughout,
 * despite the `/api/` prefix the shared-key routes also use; see
 * `BridgeConfig.isSharedKeyRoute`, which is an allowlist for exactly this reason.
 */
interface SocialApi {
    @GET("api/social/friends")
    suspend fun friends(): FriendsResponse

    @GET("api/social/friends/requests")
    suspend fun requests(): FriendRequestsResponse

    @POST("api/social/friends/request")
    suspend fun sendRequest(@Body body: FriendRequestCreate)

    @POST("api/social/friends/request/{id}/accept")
    suspend fun accept(@Path("id") requestId: String)

    @POST("api/social/friends/request/{id}/decline")
    suspend fun decline(@Path("id") requestId: String)

    @POST("api/social/friends/request/{id}/cancel")
    suspend fun cancel(@Path("id") requestId: String)

    @DELETE("api/social/friends/{id}")
    suspend fun removeFriend(@Path("id") friendId: String)

    @GET("api/social/users/search")
    suspend fun searchUsers(@Query("q") query: String, @Query("limit") limit: Int = 20): UserSearchResponse

    /** One batched call for every friend -- never loop a per-friend request. */
    @GET("api/social/presence/friends")
    suspend fun friendsPresence(): PresenceResponse

    @POST("api/social/presence")
    suspend fun updatePresence(@Body body: PresenceUpdate)

    @GET("api/social/activity/friends")
    suspend fun friendsActivity(@Query("limit") limit: Int = 40): FriendActivityResponse

    @GET("api/social/profile/{id}")
    suspend fun profile(@Path("id") userId: String): ProfileDto

    @GET("api/social/compatibility/{id}")
    suspend fun compatibility(@Path("id") userId: String): CompatibilityDto
}

package com.lumisound.android.bridge.model

import com.google.gson.annotations.SerializedName

/**
 * The bridge's `_public_user_fields`: the four columns every social route exposes about
 * another account and nothing more. `avatar_url` is rarely set; the avatar itself is
 * always served from `/user/avatar/{user_id}`, which is what the app loads.
 */
data class PublicUserDto(
    @SerializedName("user_id") val userId: String = "",
    val username: String = "",
    @SerializedName("display_name") val displayName: String? = null,
    @SerializedName("avatar_url") val avatarUrl: String? = null,
) {
    val shownName: String get() = displayName?.takeIf { it.isNotBlank() } ?: username
}

data class FriendDto(
    @SerializedName("user_id") val userId: String = "",
    val username: String = "",
    @SerializedName("display_name") val displayName: String? = null,
    @SerializedName("friends_since") val friendsSince: String? = null,
    val nickname: String? = null,
    val tags: List<String> = emptyList(),
) {
    val shownName: String get() = nickname?.takeIf { it.isNotBlank() }
        ?: displayName?.takeIf { it.isNotBlank() }
        ?: username
}

data class FriendsResponse(val friends: List<FriendDto> = emptyList())

data class FriendRequestDto(
    @SerializedName("request_id") val requestId: String = "",
    @SerializedName("user_id") val userId: String = "",
    val username: String = "",
    @SerializedName("display_name") val displayName: String? = null,
    @SerializedName("created_at") val createdAt: String? = null,
) {
    val shownName: String get() = displayName?.takeIf { it.isNotBlank() } ?: username
}

data class FriendRequestsResponse(
    val incoming: List<FriendRequestDto> = emptyList(),
    val outgoing: List<FriendRequestDto> = emptyList(),
)

/** Either field identifies the recipient; the server resolves a username itself. */
data class FriendRequestCreate(
    @SerializedName("to_user_id") val toUserId: String? = null,
    @SerializedName("to_username") val toUsername: String? = null,
)

data class UserSearchResponse(val users: List<PublicUserDto> = emptyList())

/**
 * One friend's presence. The server already applies freshness and the friend's own
 * share-now-playing toggle, so a null title here means "not shared", not "unknown".
 */
data class PresenceDto(
    @SerializedName("user_id") val userId: String = "",
    val online: Boolean = false,
    @SerializedName("is_playing") val isPlaying: Boolean = false,
    @SerializedName("now_playing_title") val nowPlayingTitle: String? = null,
    @SerializedName("now_playing_artist") val nowPlayingArtist: String? = null,
    @SerializedName("now_playing_artwork_url") val nowPlayingArtworkUrl: String? = null,
    @SerializedName("last_seen_at") val lastSeenAt: String? = null,
)

data class PresenceResponse(val presence: List<PresenceDto> = emptyList())

/** `POST /api/social/presence` -- the heartbeat Lumisound sends every 30-60s. */
data class PresenceUpdate(
    @SerializedName("is_playing") val isPlaying: Boolean = false,
    @SerializedName("now_playing_title") val nowPlayingTitle: String? = null,
    @SerializedName("now_playing_artist") val nowPlayingArtist: String? = null,
    @SerializedName("now_playing_artwork_url") val nowPlayingArtworkUrl: String? = null,
    @SerializedName("going_offline") val goingOffline: Boolean = false,
)

/** One row of `GET /api/social/activity/friends`: a friend's play or new favorite. */
data class FriendActivityDto(
    @SerializedName("user_id") val userId: String = "",
    val username: String = "",
    @SerializedName("display_name") val displayName: String? = null,
    /** `played` or `favorited`. */
    val kind: String = "played",
    val title: String? = null,
    val artist: String? = null,
    val at: String? = null,
) {
    val shownName: String get() = displayName?.takeIf { it.isNotBlank() } ?: username
}

data class FriendActivityResponse(val activity: List<FriendActivityDto> = emptyList())

data class ProfileBadgeDto(
    val id: String = "",
    val label: String = "",
    val icon: String? = null,
    val tier: String? = null,
)

data class PinnedTrackDto(
    @SerializedName("source_track_id") val sourceTrackId: String? = null,
    @SerializedName("track_url") val trackUrl: String? = null,
    val title: String? = null,
    val artist: String? = null,
    val album: String? = null,
)

/**
 * `GET /api/social/profile/{user_id}`. Only the fields this port renders are modeled;
 * Gson ignores the rest (banner, frames, effects and the other iOS-only decoration).
 */
data class ProfileDto(
    @SerializedName("user_id") val userId: String = "",
    val username: String = "",
    @SerializedName("display_name") val displayName: String? = null,
    val bio: String? = null,
    @SerializedName("main_accent_hex") val mainAccentHex: String? = null,
    val pronouns: String? = null,
    @SerializedName("status_emoji") val statusEmoji: String? = null,
    @SerializedName("status_text") val statusText: String? = null,
    @SerializedName("is_friend") val isFriend: Boolean = false,
    @SerializedName("member_since") val memberSince: String? = null,
    @SerializedName("pinned_tracks") val pinnedTracks: List<PinnedTrackDto> = emptyList(),
    @SerializedName("top_genres") val topGenres: List<String> = emptyList(),
    @SerializedName("top_artists") val topArtists: List<String> = emptyList(),
    val badges: List<ProfileBadgeDto> = emptyList(),
    /** Null when the owner hides their listening stats. */
    @SerializedName("listening_streak") val listeningStreak: StreakDto? = null,
) {
    val shownName: String get() = displayName?.takeIf { it.isNotBlank() } ?: username
}

data class StreakDto(
    @SerializedName("current_streak_days") val currentStreakDays: Int = 0,
    @SerializedName("longest_streak_days") val longestStreakDays: Int = 0,
)

/** `GET /api/social/compatibility/{user_id}` -- friends only; a stranger gets a 403. */
data class CompatibilityDto(
    val score: Int = 0,
    @SerializedName("insufficient_data") val insufficientData: Boolean = false,
    @SerializedName("shared_artists") val sharedArtists: List<String> = emptyList(),
    @SerializedName("shared_genres") val sharedGenres: List<String> = emptyList(),
    @SerializedName("sonic_score") val sonicScore: Int? = null,
    val reasons: List<String> = emptyList(),
)

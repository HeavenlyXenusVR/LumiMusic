package com.lumisound.android.playback

import android.net.Uri
import android.os.Bundle
import androidx.annotation.OptIn
import androidx.media3.common.MediaItem
import androidx.media3.common.MediaMetadata
import androidx.media3.common.util.UnstableApi
import com.lumisound.android.bridge.BridgeUrls
import com.lumisound.android.bridge.model.PodcastEpisodeDto
import com.lumisound.android.bridge.model.StreamTrackDto
import com.lumisound.android.bridge.model.WeeklyMixTrackDto
import com.lumisound.android.data.db.CloudTrackEntity
import com.lumisound.android.data.db.LocalTrackEntity

/**
 * What the player needs to know about a track, wherever it came from.
 *
 * A cloud track, an offline copy of a cloud track and a device file are three
 * different sources of bytes for one idea, and the queue should not care which it
 * is holding -- so they all become this before becoming a `MediaItem`.
 */
data class PlayableTrack(
    val id: String,
    val title: String,
    val artist: String,
    val album: String,
    val uri: String,
    val artworkUri: String?,
    val genre: String = "",
    val durationMs: Long = 0,
    val isLocked: Boolean = false,
    /** Server-relative path for a cloud track; null for a device file. */
    val serverPath: String? = null,
    val isLocal: Boolean = false,
    /**
     * The canonical page URL of a streamed YouTube/SoundCloud track. A play is logged
     * against it as `track_url`, which is what On This Day and the server's other
     * history-derived features need to find the track again.
     */
    val trackUrl: String? = null,
    /** Set only for a podcast episode, whose progress is saved rather than logged as a play. */
    val podcastFeedUrl: String? = null,
    val episodeGuid: String? = null,
)

/**
 * [downloadedPath] is the on-disk copy of this track if one exists. A downloaded
 * locked track is stored exactly as the server sent it -- still masked -- so the
 * marker travels with the file URI too and the same data source unmasks it. Keeping
 * the offline copy masked is the point: an unmasked one would be playable by
 * anything on the device, which is precisely what the format exists to prevent.
 */
fun CloudTrackEntity.toPlayable(baseUrl: String, downloadedPath: String? = null) = PlayableTrack(
    id = serverPath,
    title = title,
    artist = artist,
    album = album,
    uri = if (downloadedPath != null) {
        // A download is a file path in the phone folder, or a content:// document in a
        // folder the user chose.
        val local = if (downloadedPath.startsWith("content://")) Uri.parse(downloadedPath) else Uri.fromFile(java.io.File(downloadedPath))
        local.buildUpon()
            .apply { if (isLocked) appendQueryParameter(BridgeUrls.LOCKED_MARKER, BridgeUrls.LOCKED_MARKER_VALUE) }
            .build().toString()
    } else {
        BridgeUrls.stream(baseUrl, serverPath, isLocked)
    },
    // Always asked for: see BridgeUrls.cloudArtwork.
    artworkUri = BridgeUrls.cloudArtwork(baseUrl, serverPath),
    genre = genre,
    durationMs = (durationSeconds * 1000).toLong(),
    isLocked = isLocked,
    serverPath = serverPath,
)

fun LocalTrackEntity.toPlayable() = PlayableTrack(
    id = "local:$mediaStoreId",
    title = title,
    artist = artist,
    album = album,
    uri = contentUri,
    // MediaStore exposes album art through the album id, no separate fetch needed.
    artworkUri = "content://media/external/audio/albumart/$albumId",
    genre = genre,
    durationMs = durationMs,
    isLocal = true,
)

/**
 * A YouTube or SoundCloud track, played through the bridge's proxy. The id is namespaced by
 * source so the same video found twice (search, then a mix) is recognisably one track.
 */
fun StreamTrackDto.toPlayable(baseUrl: String) = PlayableTrack(
    id = "$source:$id",
    title = title,
    artist = artist,
    album = "",
    uri = BridgeUrls.streamProxy(baseUrl, id, source, youtubeUrl),
    artworkUri = thumbnailUrl?.takeIf { it.isNotBlank() },
    durationMs = durationSeconds * 1000L,
    trackUrl = youtubeUrl?.takeIf { it.isNotBlank() }
        ?: if (source == "youtube") "https://youtube.com/watch?v=$id" else null,
)

/**
 * A weekly-mix entry is one of the account's own uploads, so it streams exactly like a
 * cloud library track. The mirror row is preferred when there is one (it knows about
 * downloads and the real lock flag); this is the fallback for a mix that is newer than
 * the last import.
 */
fun WeeklyMixTrackDto.toPlayable(baseUrl: String): PlayableTrack {
    val locked = relativePath.endsWith(".lms", ignoreCase = true)
    return PlayableTrack(
        id = relativePath,
        title = title,
        artist = artist,
        album = album,
        uri = BridgeUrls.stream(baseUrl, relativePath, locked),
        artworkUri = BridgeUrls.cloudArtwork(baseUrl, relativePath),
        isLocked = locked,
        serverPath = relativePath,
    )
}

/** A podcast episode streams straight from its own enclosure URL; the bridge is not involved. */
fun PodcastEpisodeDto.toPlayable(feedUrl: String, showTitle: String, artworkUrl: String?): PlayableTrack? {
    val audio = audioUrl?.takeIf { it.isNotBlank() } ?: return null
    return PlayableTrack(
        id = "podcast:${guid ?: audio}",
        title = title.ifBlank { "Episode" },
        artist = showTitle,
        album = showTitle,
        uri = audio,
        artworkUri = artworkUrl,
        genre = "Podcast",
        durationMs = (durationSeconds ?: 0) * 1000L,
        podcastFeedUrl = feedUrl,
        episodeGuid = guid ?: audio,
    )
}

@OptIn(UnstableApi::class)
fun PlayableTrack.toMediaItem(): MediaItem = MediaItem.Builder()
    .setMediaId(id)
    .setUri(uri)
    .setMediaMetadata(
        MediaMetadata.Builder()
            .setTitle(title)
            .setArtist(artist)
            .setAlbumTitle(album)
            .setGenre(genre)
            .setArtworkUri(artworkUri?.let(Uri::parse))
            .setIsPlayable(true)
            .setIsBrowsable(false)
            .setExtras(
                Bundle().apply {
                    putString(EXTRA_SERVER_PATH, serverPath)
                    putBoolean(EXTRA_IS_LOCKED, isLocked)
                    putBoolean(EXTRA_IS_LOCAL, isLocal)
                    putLong(EXTRA_DURATION_MS, durationMs)
                    putString(EXTRA_TRACK_URL, trackUrl)
                    putString(EXTRA_PODCAST_FEED, podcastFeedUrl)
                    putString(EXTRA_EPISODE_GUID, episodeGuid)
                }
            )
            .build()
    )
    .build()

const val EXTRA_IS_LOCAL = "lumi.isLocal"
const val EXTRA_DURATION_MS = "lumi.durationMs"
const val EXTRA_TRACK_URL = "lumi.trackUrl"
const val EXTRA_PODCAST_FEED = "lumi.podcastFeed"
const val EXTRA_EPISODE_GUID = "lumi.episodeGuid"

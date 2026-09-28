package com.lumisound.android.playback

import android.net.Uri
import android.os.Bundle
import androidx.annotation.OptIn
import androidx.media3.common.MediaItem
import androidx.media3.common.MediaMetadata
import androidx.media3.common.util.UnstableApi
import com.lumisound.android.bridge.BridgeUrls
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
        Uri.fromFile(java.io.File(downloadedPath)).buildUpon()
            .apply { if (isLocked) appendQueryParameter(BridgeUrls.LOCKED_MARKER, BridgeUrls.LOCKED_MARKER_VALUE) }
            .build().toString()
    } else {
        BridgeUrls.stream(baseUrl, serverPath, isLocked)
    },
    artworkUri = if (hasArtwork) BridgeUrls.artwork(baseUrl, serverPath) else null,
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
                }
            )
            .build()
    )
    .build()

const val EXTRA_IS_LOCAL = "lumi.isLocal"

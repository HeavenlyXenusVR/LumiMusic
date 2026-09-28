package com.lumisound.android.data.db

import androidx.room.Entity
import androidx.room.Index
import androidx.room.PrimaryKey

/**
 * A track on the device itself, as MediaStore reports it.
 *
 * Kept in its own table rather than folded into `cloud_tracks`: the two have
 * genuinely different identity (a MediaStore id versus a server-relative path)
 * and different lifetimes (a rescan replaces these, an import replaces those), and
 * merging them would mean every row carrying half its columns as nulls.
 */
@Entity(tableName = "local_tracks", indices = [Index("title"), Index("artist"), Index("album"), Index("folder")])
data class LocalTrackEntity(
    /**
     * The content URI, not the MediaStore `_ID`: ids are unique per VOLUME, and this
     * scan covers every mounted volume, so an id alone would let a track on an SD card
     * silently replace one with the same id in internal storage.
     */
    @PrimaryKey val contentUri: String,
    val mediaStoreId: Long,
    val title: String,
    val artist: String,
    val album: String,
    val durationMs: Long,
    val genre: String,
    val trackNumber: Int,
    val year: Int,
    val sizeBytes: Long,
    val mimeType: String,
    /** Parent folder name, for the Folders view. */
    val folder: String,
    val relativePath: String,
    val dateAddedSeconds: Long,
    val albumId: Long,
)

/**
 * A cloud track saved for offline playback.
 *
 * Downloaded bytes are stored exactly as the server sent them -- a locked track's
 * file stays masked on disk, and the same data source unmasks it on the way to the
 * decoder. Saving an unmasked copy would be the one thing the lock format exists to
 * prevent, and it would mean the offline copy was playable by anything on the
 * device while the streamed one was not.
 */
@Entity(tableName = "downloads")
data class DownloadEntity(
    @PrimaryKey val serverPath: String,
    val localPath: String,
    val isLocked: Boolean,
    val sizeBytes: Long,
    val completedAt: Long,
)

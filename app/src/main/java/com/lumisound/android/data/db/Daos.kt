package com.lumisound.android.data.db

import androidx.room.Dao
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.Query
import androidx.room.Transaction
import kotlinx.coroutines.flow.Flow

@Dao
interface CloudTrackDao {
    @Query("SELECT * FROM cloud_tracks ORDER BY album COLLATE NOCASE, title COLLATE NOCASE")
    fun observeAll(): Flow<List<CloudTrackEntity>>

    @Query(
        """
        SELECT * FROM cloud_tracks
        WHERE title LIKE '%' || :query || '%'
           OR artist LIKE '%' || :query || '%'
           OR album LIKE '%' || :query || '%'
        ORDER BY title COLLATE NOCASE
        """
    )
    fun search(query: String): Flow<List<CloudTrackEntity>>

    @Query("SELECT * FROM cloud_tracks ORDER BY COALESCE(uploadedAt, '') DESC LIMIT :limit")
    fun observeRecentlyAdded(limit: Int): Flow<List<CloudTrackEntity>>

    @Query("SELECT * FROM cloud_tracks WHERE serverPath = :serverPath")
    suspend fun byPath(serverPath: String): CloudTrackEntity?

    @Query("SELECT COUNT(*) FROM cloud_tracks")
    suspend fun count(): Int

    /** Every cloud track's path: tells a favorite or history row that names one apart from a streamed track. */
    @Query("SELECT serverPath FROM cloud_tracks")
    fun observePaths(): Flow<List<String>>

    /** The whole library, oldest upload first, so a full download fills in the order it grew. */
    @Query("SELECT * FROM cloud_tracks ORDER BY COALESCE(uploadedAt, '') ASC, serverPath")
    suspend fun all(): List<CloudTrackEntity>

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun upsert(tracks: List<CloudTrackEntity>)

    /**
     * Deletes rows the last full listing didn't refresh -- i.e. tracks removed
     * server-side (or from another client) since. Scoped by the sync stamp rather
     * than "delete all then insert" so a failed page never empties the library.
     */
    @Query("DELETE FROM cloud_tracks WHERE syncedAt < :syncedAt")
    suspend fun deleteStale(syncedAt: Long)

    @Transaction
    suspend fun replaceAll(tracks: List<CloudTrackEntity>, syncedAt: Long) {
        upsert(tracks)
        deleteStale(syncedAt)
    }
}

@Dao
interface FavoriteDao {
    @Query("SELECT * FROM favorites ORDER BY COALESCE(addedAt, '') DESC")
    fun observeAll(): Flow<List<FavoriteEntity>>

    @Query("SELECT songId FROM favorites")
    fun observeIds(): Flow<List<String>>

    @Query("SELECT COUNT(*) FROM favorites")
    suspend fun count(): Int

    @Query("SELECT EXISTS(SELECT 1 FROM favorites WHERE songId = :songId)")
    suspend fun contains(songId: String): Boolean

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun upsertOne(item: FavoriteEntity)

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun upsert(items: List<FavoriteEntity>)

    @Query("DELETE FROM favorites WHERE songId = :songId")
    suspend fun delete(songId: String)

    @Query("DELETE FROM favorites")
    suspend fun clear()

    @Transaction
    suspend fun replaceAll(items: List<FavoriteEntity>) {
        clear()
        upsert(items)
    }
}

@Dao
interface PlaylistDao {
    @Query("SELECT * FROM playlists ORDER BY COALESCE(updatedAt, '') DESC")
    fun observeAll(): Flow<List<PlaylistEntity>>

    @Query("SELECT * FROM playlist_tracks WHERE playlistId = :playlistId ORDER BY position")
    fun observeTracks(playlistId: String): Flow<List<PlaylistTrackEntity>>

    @Query("SELECT * FROM playlist_tracks WHERE playlistId = :playlistId ORDER BY position")
    suspend fun tracksOf(playlistId: String): List<PlaylistTrackEntity>

    @Query("SELECT COUNT(*) FROM playlists")
    suspend fun count(): Int

    @Query("DELETE FROM playlists WHERE id = :id")
    suspend fun deletePlaylist(id: String)

    @Query("DELETE FROM playlist_tracks WHERE playlistId = :id")
    suspend fun deleteTracksOf(id: String)

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun upsertPlaylists(items: List<PlaylistEntity>)

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun upsertTracks(items: List<PlaylistTrackEntity>)

    @Query("DELETE FROM playlists")
    suspend fun clearPlaylists()

    @Query("DELETE FROM playlist_tracks")
    suspend fun clearTracks()

    @Transaction
    suspend fun replaceAll(playlists: List<PlaylistEntity>, tracks: List<PlaylistTrackEntity>) {
        clearTracks()
        clearPlaylists()
        upsertPlaylists(playlists)
        upsertTracks(tracks)
    }
}

@Dao
interface PlayHistoryDao {
    @Query("SELECT * FROM play_history ORDER BY COALESCE(playedAt, '') DESC")
    fun observeAll(): Flow<List<PlayHistoryEntity>>

    @Query("SELECT COUNT(*) FROM play_history")
    suspend fun count(): Int

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun upsert(items: List<PlayHistoryEntity>)

    @Query("DELETE FROM play_history")
    suspend fun clear()

    @Transaction
    suspend fun replaceAll(items: List<PlayHistoryEntity>) {
        clear()
        upsert(items)
    }
}

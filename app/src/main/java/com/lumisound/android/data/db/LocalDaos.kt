package com.lumisound.android.data.db

import androidx.room.Dao
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.Query
import androidx.room.Transaction
import kotlinx.coroutines.flow.Flow

@Dao
interface LocalTrackDao {
    @Query("SELECT * FROM local_tracks ORDER BY title COLLATE NOCASE")
    fun observeAll(): Flow<List<LocalTrackEntity>>

    @Query(
        """
        SELECT * FROM local_tracks
        WHERE title LIKE '%' || :query || '%'
           OR artist LIKE '%' || :query || '%'
           OR album LIKE '%' || :query || '%'
        ORDER BY title COLLATE NOCASE
        """
    )
    fun search(query: String): Flow<List<LocalTrackEntity>>

    @Query("SELECT DISTINCT artist FROM local_tracks WHERE artist != '' ORDER BY artist COLLATE NOCASE")
    fun observeArtists(): Flow<List<String>>

    @Query("SELECT DISTINCT album FROM local_tracks WHERE album != '' ORDER BY album COLLATE NOCASE")
    fun observeAlbums(): Flow<List<String>>

    @Query("SELECT DISTINCT folder FROM local_tracks WHERE folder != '' ORDER BY folder COLLATE NOCASE")
    fun observeFolders(): Flow<List<String>>

    @Query("SELECT * FROM local_tracks WHERE artist = :artist ORDER BY album COLLATE NOCASE, trackNumber")
    fun observeByArtist(artist: String): Flow<List<LocalTrackEntity>>

    @Query("SELECT * FROM local_tracks WHERE album = :album ORDER BY trackNumber, title COLLATE NOCASE")
    fun observeByAlbum(album: String): Flow<List<LocalTrackEntity>>

    @Query("SELECT * FROM local_tracks WHERE folder = :folder ORDER BY title COLLATE NOCASE")
    fun observeByFolder(folder: String): Flow<List<LocalTrackEntity>>

    @Query("SELECT COUNT(*) FROM local_tracks")
    suspend fun count(): Int

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun upsert(tracks: List<LocalTrackEntity>)

    @Query("DELETE FROM local_tracks WHERE contentUri NOT IN (:keepUris)")
    suspend fun deleteMissing(keepUris: List<String>)

    @Query("DELETE FROM local_tracks")
    suspend fun clear()

    /**
     * A rescan replaces the table, but only ever by upserting what was found and
     * then dropping what was not -- never "delete all, then insert", which would
     * empty a user's library if the scan failed halfway.
     */
    @Transaction
    suspend fun replaceAll(tracks: List<LocalTrackEntity>) {
        upsert(tracks)
        if (tracks.isEmpty()) clear() else deleteMissing(tracks.map { it.contentUri })
    }
}

@Dao
interface DownloadDao {
    @Query("SELECT * FROM downloads")
    fun observeAll(): Flow<List<DownloadEntity>>

    @Query("SELECT * FROM downloads WHERE serverPath = :serverPath")
    suspend fun byPath(serverPath: String): DownloadEntity?

    @Query("SELECT serverPath FROM downloads")
    fun observePaths(): Flow<List<String>>

    @Query("SELECT COUNT(*) FROM downloads")
    suspend fun count(): Int

    @Query("SELECT COALESCE(SUM(sizeBytes), 0) FROM downloads")
    suspend fun totalBytes(): Long

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun upsert(item: DownloadEntity)

    @Query("DELETE FROM downloads WHERE serverPath = :serverPath")
    suspend fun delete(serverPath: String)
}

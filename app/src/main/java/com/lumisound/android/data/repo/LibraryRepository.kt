package com.lumisound.android.data.repo

import com.lumisound.android.bridge.BridgeHttp
import com.lumisound.android.bridge.model.AddFavoriteRequest
import com.lumisound.android.bridge.model.CreatePlaylistRequest
import com.lumisound.android.bridge.model.PlaylistTrackRequest
import com.lumisound.android.bridge.model.UpdatePlaylistRequest
import com.lumisound.android.data.db.FavoriteEntity
import com.lumisound.android.data.db.LumiDatabase
import com.lumisound.android.data.db.PlaylistEntity
import com.lumisound.android.data.db.PlaylistTrackEntity
import com.lumisound.android.diagnostics.AppLogger
import com.lumisound.android.diagnostics.RemoteLogger

/**
 * Writes to the account's shared library -- favorites and playlists -- and keeps the
 * local mirror in step.
 *
 * Every write goes to the bridge first and is only mirrored locally once the server
 * has accepted it. The mirror is the account's data as the server knows it, and a
 * local row that the server never got would show a favorite on this device that
 * exists nowhere else, which is worse than the write visibly failing.
 */
class LibraryRepository(
    private val http: BridgeHttp,
    private val database: LumiDatabase,
    private val remote: RemoteLogger,
) {

    suspend fun isFavorite(songId: String): Boolean = database.favorites().contains(songId)

    /** Returns the new state, or null if the server refused. */
    suspend fun toggleFavorite(songId: String, title: String?, artist: String?, album: String?): Boolean? = try {
        if (database.favorites().contains(songId)) {
            http.libraryData.removeFavorite(songId)
            database.favorites().delete(songId)
            false
        } else {
            http.libraryData.addFavorite(AddFavoriteRequest(songId, title, artist, album))
            database.favorites().upsertOne(
                FavoriteEntity(songId = songId, title = title, artist = artist, album = album, addedAt = null)
            )
            true
        }
    } catch (e: Exception) {
        AppLogger.e("favorites", "toggle failed for $songId", e)
        null
    }

    suspend fun createPlaylist(name: String, description: String?): String? = try {
        val created = http.libraryData.createPlaylist(CreatePlaylistRequest(name = name, description = description))
        database.playlists().upsertPlaylists(
            listOf(
                PlaylistEntity(
                    id = created.id,
                    name = created.name,
                    description = created.description,
                    folder = created.folder,
                    updatedAt = created.updatedAt,
                    trackCount = 0,
                )
            )
        )
        remote.log("playlists", "created", message = name)
        created.id
    } catch (e: Exception) {
        AppLogger.e("playlists", "create failed", e)
        null
    }

    suspend fun renamePlaylist(id: String, name: String): Boolean = try {
        http.libraryData.updatePlaylist(id, UpdatePlaylistRequest(name = name))
        refreshPlaylist(id)
        true
    } catch (e: Exception) {
        AppLogger.e("playlists", "rename failed", e)
        false
    }

    suspend fun deletePlaylist(id: String): Boolean = try {
        http.libraryData.deletePlaylist(id)
        database.playlists().deleteTracksOf(id)
        database.playlists().deletePlaylist(id)
        true
    } catch (e: Exception) {
        AppLogger.e("playlists", "delete failed", e)
        false
    }

    suspend fun addToPlaylist(
        playlistId: String,
        title: String,
        artist: String?,
        album: String?,
        localSongId: String?,
        durationSeconds: Int?,
    ): Boolean = try {
        val position = database.playlists().tracksOf(playlistId).size
        http.libraryData.addPlaylistTrack(
            playlistId,
            PlaylistTrackRequest(
                title = title,
                artist = artist,
                album = album,
                localSongId = localSongId,
                durationSeconds = durationSeconds ?: 0,
                position = position,
            ),
        )
        // Re-read rather than guessing the row the server made: it assigns the track
        // its own id, which is what a later removal needs.
        refreshPlaylist(playlistId)
        true
    } catch (e: Exception) {
        AppLogger.e("playlists", "add track failed", e)
        false
    }

    suspend fun removeFromPlaylist(playlistId: String, trackId: String): Boolean = try {
        http.libraryData.removePlaylistTrack(playlistId, trackId)
        refreshPlaylist(playlistId)
        true
    } catch (e: Exception) {
        AppLogger.e("playlists", "remove track failed", e)
        false
    }

    /** Pulls one playlist back down and replaces its local rows. */
    suspend fun refreshPlaylist(id: String) {
        val playlist = http.libraryData.playlist(id)
        database.playlists().upsertPlaylists(
            listOf(
                PlaylistEntity(
                    id = playlist.id,
                    name = playlist.name,
                    description = playlist.description,
                    folder = playlist.folder,
                    updatedAt = playlist.updatedAt,
                    trackCount = playlist.tracks.size,
                )
            )
        )
        database.playlists().deleteTracksOf(id)
        database.playlists().upsertTracks(
            playlist.tracks.mapIndexed { index, track ->
                PlaylistTrackEntity(
                    playlistId = id,
                    position = index,
                    remoteId = track.id,
                    trackUrl = track.trackUrl,
                    localSongId = track.localSongId,
                    title = track.title,
                    artist = track.artist,
                    album = track.album,
                    durationSeconds = track.durationSeconds,
                )
            }
        )
    }
}

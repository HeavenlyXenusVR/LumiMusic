package com.lumisound.android.library

import android.content.ContentUris
import android.content.Context
import android.provider.MediaStore
import com.lumisound.android.data.db.LocalTrackEntity
import com.lumisound.android.data.db.LumiDatabase
import com.lumisound.android.diagnostics.AppLogger
import com.lumisound.android.diagnostics.RemoteLogger
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.withContext

data class ScanState(
    val running: Boolean = false,
    val found: Int = 0,
    val lastRunAt: Long? = null,
    val lastDurationMs: Long? = null,
    val error: String? = null,
)

/**
 * Reads the device's own music with one MediaStore query.
 *
 * One query, one pass, one database write: a per-file metadata read (or a
 * `MediaMetadataRetriever` per track) is the standard way this ends up taking
 * minutes on a real library, and MediaStore already holds everything shown in a
 * list. Anything that genuinely needs the file itself -- BPM, replay gain -- belongs
 * in a later pass over rows that already exist, not in the scan.
 */
class LibraryScanner(
    private val context: Context,
    private val database: LumiDatabase,
    private val remote: RemoteLogger,
) {

    private val _state = MutableStateFlow(ScanState())
    val state: StateFlow<ScanState> = _state.asStateFlow()

    suspend fun scan(): ScanState = withContext(Dispatchers.IO) {
        if (_state.value.running) return@withContext _state.value
        // Checked before querying, not inferred from the result: without the
        // permission MediaStore hands back an empty cursor instead of throwing, so a
        // denied scan is indistinguishable from an empty device unless asked directly.
        if (!AudioPermission.isGranted(context)) {
            AppLogger.w("library", "scan skipped: ${AudioPermission.required} not granted")
            remote.log(
                "library",
                "scan_denied",
                level = "warn",
                message = "${AudioPermission.required} not granted",
                detail = mapOf("api" to android.os.Build.VERSION.SDK_INT),
            )
            _state.value = _state.value.copy(
                running = false,
                error = "LumiMusic needs permission to read audio files. Tap Rescan to grant it.",
                lastRunAt = System.currentTimeMillis(),
            )
            return@withContext _state.value
        }

        _state.value = _state.value.copy(running = true, error = null)
        val startedAt = System.currentTimeMillis()

        val tracks = try {
            query()
        } catch (e: SecurityException) {
            // The audio permission was never granted or was revoked. This is the
            // single most likely reason a library looks empty, so it is reported as
            // its own state rather than an anonymous failure.
            AppLogger.w("library", "scan denied: no audio permission")
            _state.value = _state.value.copy(
                running = false,
                error = "Permission to read audio was denied.",
                lastRunAt = System.currentTimeMillis(),
            )
            remote.log("library", "scan_denied", level = "warn", message = "READ_MEDIA_AUDIO not granted")
            return@withContext _state.value
        } catch (e: Exception) {
            AppLogger.e("library", "scan failed", e)
            _state.value = _state.value.copy(
                running = false,
                error = "${e.javaClass.simpleName}: ${e.message}",
                lastRunAt = System.currentTimeMillis(),
            )
            remote.log("library", "scan_failed", level = "error", message = e.message)
            return@withContext _state.value
        }

        database.localTracks().replaceAll(tracks)
        val durationMs = System.currentTimeMillis() - startedAt
        _state.value = ScanState(
            running = false,
            found = tracks.size,
            lastRunAt = System.currentTimeMillis(),
            lastDurationMs = durationMs,
        )
        // One event per scan, with counts -- never one per track.
        remote.log(
            "library",
            "scan_completed",
            message = "${tracks.size} tracks in ${durationMs}ms",
            detail = mapOf(
                "tracks" to tracks.size,
                "durationMs" to durationMs,
                "artists" to tracks.map { it.artist }.distinct().size,
                "albums" to tracks.map { it.album }.distinct().size,
                "folders" to tracks.map { it.folder }.distinct().size,
            ),
        )
        _state.value
    }

    private fun query(): List<LocalTrackEntity> {
        val collection = MediaStore.Audio.Media.EXTERNAL_CONTENT_URI
        val projection = arrayOf(
            MediaStore.Audio.Media._ID,
            MediaStore.Audio.Media.TITLE,
            MediaStore.Audio.Media.ARTIST,
            MediaStore.Audio.Media.ALBUM,
            MediaStore.Audio.Media.DURATION,
            MediaStore.Audio.Media.TRACK,
            MediaStore.Audio.Media.YEAR,
            MediaStore.Audio.Media.SIZE,
            MediaStore.Audio.Media.MIME_TYPE,
            MediaStore.Audio.Media.RELATIVE_PATH,
            MediaStore.Audio.Media.DATE_ADDED,
            MediaStore.Audio.Media.ALBUM_ID,
            MediaStore.Audio.Media.GENRE,
        )
        val tracks = ArrayList<LocalTrackEntity>(512)
        context.contentResolver.query(
            collection,
            projection,
            "${MediaStore.Audio.Media.IS_MUSIC} != 0",
            null,
            null,
        )?.use { cursor ->
            val id = cursor.getColumnIndexOrThrow(MediaStore.Audio.Media._ID)
            val title = cursor.getColumnIndexOrThrow(MediaStore.Audio.Media.TITLE)
            val artist = cursor.getColumnIndexOrThrow(MediaStore.Audio.Media.ARTIST)
            val album = cursor.getColumnIndexOrThrow(MediaStore.Audio.Media.ALBUM)
            val duration = cursor.getColumnIndexOrThrow(MediaStore.Audio.Media.DURATION)
            val track = cursor.getColumnIndexOrThrow(MediaStore.Audio.Media.TRACK)
            val year = cursor.getColumnIndexOrThrow(MediaStore.Audio.Media.YEAR)
            val size = cursor.getColumnIndexOrThrow(MediaStore.Audio.Media.SIZE)
            val mime = cursor.getColumnIndexOrThrow(MediaStore.Audio.Media.MIME_TYPE)
            val relative = cursor.getColumnIndexOrThrow(MediaStore.Audio.Media.RELATIVE_PATH)
            val added = cursor.getColumnIndexOrThrow(MediaStore.Audio.Media.DATE_ADDED)
            val albumId = cursor.getColumnIndexOrThrow(MediaStore.Audio.Media.ALBUM_ID)
            val genre = cursor.getColumnIndex(MediaStore.Audio.Media.GENRE)

            while (cursor.moveToNext()) {
                val mediaId = cursor.getLong(id)
                val relativePath = cursor.getStringOrEmpty(relative)
                tracks.add(
                    LocalTrackEntity(
                        mediaStoreId = mediaId,
                        contentUri = ContentUris.withAppendedId(collection, mediaId).toString(),
                        title = cursor.getStringOrEmpty(title).ifBlank { "Unknown title" },
                        artist = cursor.getStringOrEmpty(artist).takeUnless { it == "<unknown>" }.orEmpty(),
                        album = cursor.getStringOrEmpty(album),
                        durationMs = cursor.getLong(duration),
                        // MediaStore encodes disc+track as DTTT (disc 1, track 4 -> 1004),
                        // so a raw value is meaningless for sorting within an album.
                        trackNumber = cursor.getInt(track).let { if (it > 1000) it % 1000 else it },
                        year = cursor.getInt(year),
                        sizeBytes = cursor.getLong(size),
                        mimeType = cursor.getStringOrEmpty(mime),
                        folder = relativePath.trimEnd('/').substringAfterLast('/'),
                        relativePath = relativePath,
                        dateAddedSeconds = cursor.getLong(added),
                        albumId = cursor.getLong(albumId),
                        genre = if (genre >= 0) cursor.getStringOrEmpty(genre) else "",
                    )
                )
            }
        }
        return tracks
    }
}

private fun android.database.Cursor.getStringOrEmpty(index: Int): String =
    if (index < 0 || isNull(index)) "" else getString(index).orEmpty()

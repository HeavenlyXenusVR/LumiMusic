package com.lumisound.android.download

import com.google.gson.GsonBuilder
import com.lumisound.android.data.db.CloudTrackEntity

/**
 * Where a downloaded track lives in the offline folder, and the metadata saved beside it.
 *
 * Laid out like any music folder -- `Artist/Album/07 Title.ext` -- so the collection
 * makes sense in a file manager or copied to a computer. Each track gets two
 * neighbours with the same base name:
 *
 * - `.json`: every field the cloud library holds for it. For a locked `.lms` track this
 *   is the only readable metadata, since its tags sit inside the masked bytes.
 * - `.jpg`: its cover, when the bridge has one.
 */
object TrackFiles {

    const val UNKNOWN_ARTIST = "Unknown artist"
    const val UNKNOWN_ALBUM = "Unknown album"

    /** The folders under the offline root: artist, then album. */
    fun folders(track: CloudTrackEntity): List<String> = listOf(
        clean(track.artist.ifBlank { UNKNOWN_ARTIST }),
        clean(track.album.ifBlank { UNKNOWN_ALBUM }),
    )

    /**
     * "07 Midnight City", or just the title when there is no usable track number.
     * [disambiguator] is added only when two different tracks would otherwise share a
     * name in the same album folder.
     */
    fun baseName(track: CloudTrackEntity, disambiguator: String? = null): String {
        val number = track.trackNumber.substringBefore('/').trim().toIntOrNull()?.takeIf { it in 1..999 }
        val title = track.title.ifBlank { track.filename.substringBeforeLast('.').ifBlank { track.serverPath.substringAfterLast('/') } }
        val name = listOfNotNull(number?.toString()?.padStart(2, '0'), clean(title)).joinToString(" ")
        return if (disambiguator == null) name else "$name [$disambiguator]"
    }

    /** The audio file's extension: the server's own, and always `lms` for a locked track. */
    fun extension(track: CloudTrackEntity): String {
        if (track.isLocked) return "lms"
        val ext = track.ext.trimStart('.').ifBlank { track.filename.substringAfterLast('.', "") }
        return ext.lowercase().filter { it.isLetterOrDigit() }.take(8).ifBlank { "audio" }
    }

    /** A short, stable tag for a server path, for [baseName]'s disambiguator. */
    fun shortHash(serverPath: String): String =
        Integer.toHexString(serverPath.hashCode()).padStart(8, '0').takeLast(6)

    /**
     * A name any filesystem accepts: no path separators, no characters FAT or Windows
     * reject, no leading or trailing dots and spaces, and short enough that a full
     * `Artist/Album/Title.ext` path stays well under the usual limits.
     */
    fun clean(text: String): String {
        val replaced = text
            .map { c -> if (c < ' ' || c in "\\/:*?\"<>|") '_' else c }
            .joinToString("")
            .trim(' ', '.')
        val shortened = if (replaced.length > MAX_SEGMENT) replaced.take(MAX_SEGMENT).trimEnd(' ', '.') else replaced
        return shortened.ifBlank { "_" }
    }

    /** The `.json` saved beside a track: the cloud library's row for it, in plain words. */
    fun sidecarJson(track: CloudTrackEntity, artworkFile: String?, savedAtIso: String, appVersion: String): String {
        val fields = linkedMapOf<String, Any?>(
            "title" to track.title,
            "artist" to track.artist,
            "album" to track.album,
            "genre" to track.genre.ifBlank { null },
            "track_number" to track.trackNumber.ifBlank { null },
            "duration_seconds" to track.durationSeconds.takeIf { it > 0 },
            "bpm" to track.bpm,
            "format" to extension(track),
            "locked" to track.isLocked,
            "lock_note" to if (track.isLocked) {
                "Lumisound-locked (LMSLOCK1). Plays in Lumisound and LumiMusic; the audio is masked on disk."
            } else null,
            "artwork" to artworkFile,
            "server_path" to track.serverPath,
            "original_filename" to track.filename.ifBlank { null },
            "uploaded_at" to track.uploadedAt,
            "saved_at" to savedAtIso,
            "saved_by" to "LumiMusic $appVersion",
        )
        return GsonBuilder().setPrettyPrinting().create().toJson(fields.filterValues { it != null })
    }

    private const val MAX_SEGMENT = 80
}

package com.lumisound.android.lyrics

import com.lumisound.android.bridge.BridgeHttp
import com.lumisound.android.diagnostics.AppLogger
import java.util.concurrent.ConcurrentHashMap

/** One timed line of synced lyrics. */
data class LyricLine(val timeMs: Long, val text: String)

/** What the lyrics panel can show for a track. */
sealed interface LyricsResult {
    data class Synced(val lines: List<LyricLine>) : LyricsResult
    data class Plain(val text: String) : LyricsResult
    data object None : LyricsResult
}

/**
 * Parses LRC, the format LRCLIB and the bridge's own cache store synced lyrics in.
 *
 * Tolerant on purpose, because real LRC files are not tidy: a line can carry several
 * timestamps (a repeated chorus written once), fractions come as centiseconds or
 * milliseconds, metadata tags like `[ar:…]` sit among the lines, and an offset tag shifts
 * everything. Anything that does not parse is skipped rather than failing the whole file.
 */
object LrcParser {

    private val timeTag = Regex("""\[(\d{1,3}):(\d{1,2})(?:[.:](\d{1,3}))?]""")
    private val offsetTag = Regex("""\[offset:\s*([+-]?\d+)\s*]""", RegexOption.IGNORE_CASE)

    fun parse(lrc: String): List<LyricLine> {
        // A positive offset means the lyrics should appear sooner.
        val offset = offsetTag.find(lrc)?.groupValues?.get(1)?.toLongOrNull() ?: 0L
        val lines = mutableListOf<LyricLine>()
        for (raw in lrc.lineSequence()) {
            val tags = timeTag.findAll(raw).toList()
            if (tags.isEmpty()) continue
            val text = raw.substring(tags.last().range.last + 1).trim()
            for (tag in tags) {
                val minutes = tag.groupValues[1].toLong()
                val seconds = tag.groupValues[2].toLong()
                val fraction = tag.groupValues[3]
                val fractionMs = when (fraction.length) {
                    0 -> 0L
                    1 -> fraction.toLong() * 100
                    2 -> fraction.toLong() * 10
                    else -> fraction.take(3).toLong()
                }
                val time = (minutes * 60_000 + seconds * 1000 + fractionMs - offset).coerceAtLeast(0)
                lines += LyricLine(time, text)
            }
        }
        return lines.sortedBy { it.timeMs }
    }

    /**
     * The line playing at [positionMs]: the last one whose time has been reached, or -1
     * before the first. A binary search, since this runs on every half-second tick.
     */
    fun activeIndex(lines: List<LyricLine>, positionMs: Long): Int {
        var low = 0
        var high = lines.lastIndex
        var found = -1
        while (low <= high) {
            val mid = (low + high) ushr 1
            if (lines[mid].timeMs <= positionMs) {
                found = mid
                low = mid + 1
            } else {
                high = mid - 1
            }
        }
        return found
    }
}

/**
 * Fetches lyrics through `GET /user/lyrics` -- the account-token route onto the same
 * shared cache `/api/lyrics` reads, which is also where Aria's transcriptions and anyone's
 * corrections from the phone end up.
 *
 * Remembered per title/artist for the life of the process, misses included: Now Playing
 * asks again every time it is opened, and a track with no lyrics should cost one request,
 * not one per open.
 */
class LyricsRepository(private val http: BridgeHttp) {

    private val cache = ConcurrentHashMap<String, LyricsResult>()

    suspend fun lyricsFor(title: String, artist: String?, durationMs: Long): LyricsResult {
        val key = "${title.trim().lowercase()}|${artist.orEmpty().trim().lowercase()}"
        cache[key]?.let { return it }
        val result = try {
            val dto = http.discovery.lyrics(
                title = title,
                artist = artist.orEmpty(),
                durationSeconds = (durationMs / 1000).toInt().takeIf { it > 0 },
            )
            val synced = dto.syncedLyrics?.let(LrcParser::parse).orEmpty().filter { it.text.isNotBlank() }
            when {
                synced.isNotEmpty() -> LyricsResult.Synced(synced)
                !dto.plainLyrics.isNullOrBlank() -> LyricsResult.Plain(dto.plainLyrics.trim())
                else -> LyricsResult.None
            }
        } catch (e: Exception) {
            // Not cached: a network failure is not the same as "this song has no lyrics".
            AppLogger.w("lyrics", "lookup failed: ${e.javaClass.simpleName}")
            return LyricsResult.None
        }
        cache[key] = result
        return result
    }
}

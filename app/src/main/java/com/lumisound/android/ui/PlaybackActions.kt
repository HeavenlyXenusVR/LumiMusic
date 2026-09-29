package com.lumisound.android.ui

import com.lumisound.android.AppContainer
import com.lumisound.android.bridge.model.StreamTrackDto
import com.lumisound.android.bridge.model.WeeklyMixTrackDto
import com.lumisound.android.diagnostics.AppLogger
import com.lumisound.android.playback.PlayableTrack
import com.lumisound.android.playback.toPlayable

/**
 * The few ways the new screens start playback, in one place so every entry point turns a
 * bridge result into a queue the same way.
 */
fun AppContainer.playStreams(tracks: List<StreamTrackDto>, startIndex: Int, shuffle: Boolean = false) {
    if (tracks.isEmpty()) return
    player.setShuffle(shuffle)
    player.play(tracks.map { it.toPlayable(config.baseUrl) }, startIndex)
}

fun AppContainer.playStreamNext(track: StreamTrackDto) = player.playNext(track.toPlayable(config.baseUrl))

fun AppContainer.enqueueStream(track: StreamTrackDto) = player.enqueue(listOf(track.toPlayable(config.baseUrl)))

/**
 * Seeds a queue from YouTube's own mix for [seed], with the seed itself first. Returns
 * false when the bridge could not build one, so the caller can say so.
 */
suspend fun AppContainer.startRadio(seed: StreamTrackDto): Boolean = try {
    val related = http.streaming.radio(seed.id, seed.source)
    playStreams(listOf(seed) + related, 0)
    AppLogger.i("radio", "radio started", mapOf("count" to related.size + 1))
    true
} catch (e: Exception) {
    AppLogger.w("radio", "radio failed: ${e.javaClass.simpleName}")
    false
}

/**
 * For things the server only knows by name -- community trending, a friend's pinned track,
 * a stats row: search for it and play the best match. Returns false when nothing matched.
 */
suspend fun AppContainer.searchAndPlay(title: String, artist: String?): Boolean = try {
    val query = listOfNotNull(artist?.takeIf { it.isNotBlank() }, title).joinToString(" ")
    val results = http.streaming.search(query, limit = 5)
    if (results.isEmpty()) false else {
        playStreams(results.take(1), 0)
        true
    }
} catch (e: Exception) {
    AppLogger.w("search", "search-and-play failed: ${e.javaClass.simpleName}")
    false
}

/**
 * Weekly-mix entries prefer the local mirror row for each track -- it knows the real lock
 * flag and whether there is an offline copy -- and fall back to building a stream from the
 * mix entry for anything uploaded since the last import.
 */
suspend fun AppContainer.weeklyMixPlayables(tracks: List<WeeklyMixTrackDto>): List<PlayableTrack> =
    tracks.map { entry ->
        val mirrored = database.cloudTracks().byPath(entry.relativePath)
        mirrored?.toPlayable(config.baseUrl, downloads.isDownloaded(entry.relativePath))
            ?: entry.toPlayable(config.baseUrl)
    }

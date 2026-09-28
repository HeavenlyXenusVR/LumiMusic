package com.lumisound.android.bridge

import java.util.Collections

/**
 * Per-track playback gain in dB, as published by the stream response's
 * `X-Loudness-Gain-Db` header. Keyed by the track's server-relative path.
 *
 * Bounded: a long session streaming a big library would otherwise accumulate a
 * map entry per track forever, and the value is cheap to re-learn on the next
 * play of an evicted track.
 */
class LoudnessGainStore(private val maxEntries: Int = 512) {

    private val gains = Collections.synchronizedMap(
        object : LinkedHashMap<String, Float>(64, 0.75f, true) {
            override fun removeEldestEntry(eldest: MutableMap.MutableEntry<String, Float>?): Boolean =
                size > maxEntries
        }
    )

    fun put(serverPath: String, gainDb: Float) {
        gains[serverPath] = gainDb
    }

    fun gainDb(serverPath: String?): Float? = serverPath?.let { gains[it] }
}

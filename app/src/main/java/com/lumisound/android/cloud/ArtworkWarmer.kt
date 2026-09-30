package com.lumisound.android.cloud

import android.content.Context
import coil3.SingletonImageLoader
import coil3.request.CachePolicy
import coil3.request.ErrorResult
import coil3.request.ImageRequest
import com.lumisound.android.bridge.BridgeConfig
import com.lumisound.android.bridge.BridgeUrls
import com.lumisound.android.data.db.LumiDatabase
import com.lumisound.android.diagnostics.AppLogger
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.async
import kotlinx.coroutines.awaitAll
import kotlinx.coroutines.isActive
import kotlinx.coroutines.launch
import kotlinx.coroutines.sync.Semaphore
import kotlinx.coroutines.sync.withPermit
import java.util.concurrent.atomic.AtomicInteger

/**
 * Fetches every cloud track's cover into the image disk cache in the background, so
 * scrolling the library shows real covers at once instead of filling them in one
 * network round-trip at a time.
 *
 * It also does the bridge a favour: a cover the bridge has not looked for yet (most
 * locked tracks) is found on this first request and cached server-side, so every later
 * request, from this phone or Lumisound, is instant.
 *
 * Newest uploads first, four at a time, and only once per library listing: a warm run
 * over a library whose covers are already cached costs a disk lookup per track.
 */
class ArtworkWarmer(
    private val context: Context,
    private val database: LumiDatabase,
    private val config: BridgeConfig,
    private val scope: CoroutineScope,
) {
    private var job: Job? = null

    fun warm() {
        if (job?.isActive == true) return
        job = scope.launch(Dispatchers.IO) {
            val paths = database.cloudTracks().all().asReversed().map { it.serverPath }
            if (paths.isEmpty()) return@launch
            val loader = SingletonImageLoader.get(context)
            val gate = Semaphore(PARALLEL)
            val missing = AtomicInteger()
            paths.chunked(64).forEach { chunk ->
                if (!isActive) return@launch
                chunk.map { path ->
                    async {
                        gate.withPermit {
                            val request = ImageRequest.Builder(context)
                                .data(BridgeUrls.artwork(config.baseUrl, path))
                                // Only the bytes are wanted now; decoding happens when a row shows it.
                                .memoryCachePolicy(CachePolicy.DISABLED)
                                .size(THUMB_PX)
                                .build()
                            if (loader.execute(request) is ErrorResult) missing.incrementAndGet()
                        }
                    }
                }.awaitAll()
            }
            AppLogger.i("artwork", "warmed ${paths.size} covers (${missing.get()} without one)")
        }
    }

    private companion object {
        const val PARALLEL = 4
        const val THUMB_PX = 256
    }
}

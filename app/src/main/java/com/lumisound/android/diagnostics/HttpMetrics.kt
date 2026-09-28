package com.lumisound.android.diagnostics

import okhttp3.Interceptor
import okhttp3.Response
import java.util.concurrent.ConcurrentHashMap
import java.util.concurrent.atomic.AtomicLong

/**
 * Per-endpoint request counts, failures and latency, collected from the app's own
 * OkHttp client.
 *
 * This is here because of a specific shape of bug this project has hit before on
 * iOS: a request that fails for an unremarkable reason (a stale token, a header
 * dropped on a byte-range request) presents to the user as a feature that simply
 * does nothing, with no error anywhere. Counting outcomes per route means the
 * diagnostics snapshot can say "every call to this endpoint 401'd" without anyone
 * having to reproduce it first.
 */
class HttpMetrics {

    data class Stat(
        val calls: Long,
        val failures: Long,
        val lastStatus: Int?,
        val totalMs: Long,
        val slowestMs: Long,
    ) {
        val averageMs: Long get() = if (calls == 0L) 0 else totalMs / calls
    }

    private class Mutable {
        val calls = AtomicLong()
        val failures = AtomicLong()
        val totalMs = AtomicLong()
        val slowestMs = AtomicLong()

        @Volatile
        var lastStatus: Int? = null
    }

    private val routes = ConcurrentHashMap<String, Mutable>()

    fun snapshot(): Map<String, Stat> = routes.entries.associate { (route, m) ->
        route to Stat(
            calls = m.calls.get(),
            failures = m.failures.get(),
            lastStatus = m.lastStatus,
            totalMs = m.totalMs.get(),
            slowestMs = m.slowestMs.get(),
        )
    }

    fun failingRoutes(): Map<String, Stat> = snapshot().filterValues { it.failures > 0 }

    fun interceptor(): Interceptor = Interceptor { chain ->
        val request = chain.request()
        val route = normaliseRoute(request.method, request.url.encodedPath)
        val startedAt = System.nanoTime()
        val stat = routes.getOrPut(route) { Mutable() }
        stat.calls.incrementAndGet()
        try {
            val response: Response = chain.proceed(request)
            record(stat, startedAt, response.code, failed = !response.isSuccessful)
            if (!response.isSuccessful) {
                AppLogger.w("http", "$route -> ${response.code}")
            }
            response
        } catch (e: Exception) {
            record(stat, startedAt, status = null, failed = true)
            AppLogger.w("http", "$route failed: ${e.javaClass.simpleName}: ${e.message}")
            throw e
        }
    }

    private fun record(stat: Mutable, startedAt: Long, status: Int?, failed: Boolean) {
        val ms = (System.nanoTime() - startedAt) / 1_000_000
        stat.totalMs.addAndGet(ms)
        stat.lastStatus = status
        if (failed) stat.failures.incrementAndGet()
        while (true) {
            val current = stat.slowestMs.get()
            if (ms <= current || stat.slowestMs.compareAndSet(current, ms)) break
        }
    }

    /**
     * Collapses the ids out of a path so `/user/playlists/<uuid>/collaborators`
     * counts as one route rather than one per playlist -- otherwise the map grows
     * without bound and every row has a count of 1.
     */
    private fun normaliseRoute(method: String, path: String): String {
        val collapsed = path.split('/').joinToString("/") { segment ->
            when {
                segment.length >= 32 && segment.count { it == '-' } >= 4 -> "{id}"
                segment.length >= 24 && segment.all { it.isLetterOrDigit() } -> "{id}"
                segment.isNotEmpty() && segment.all { it.isDigit() } -> "{n}"
                else -> segment
            }
        }
        return "$method $collapsed"
    }
}

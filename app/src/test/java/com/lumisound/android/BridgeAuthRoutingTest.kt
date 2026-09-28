package com.lumisound.android

import com.lumisound.android.bridge.BridgeConfig
import okhttp3.OkHttpClient
import okhttp3.Request
import okhttp3.mockwebserver.MockResponse
import okhttp3.mockwebserver.MockWebServer
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test

/**
 * Which credential each route gets.
 *
 * Artwork loaded through a client that had never heard of the account for an entire
 * release: `/user/music/artwork` is JWT-gated and answers 401 without a token, so every
 * thumbnail failed silently while the avatar -- the one image route needing no auth -- kept
 * working and disguised it. These assert the routing decision directly, since the failure
 * mode of getting it wrong is invisible rather than loud.
 */
class BridgeAuthRoutingTest {

    private lateinit var server: MockWebServer

    @Before
    fun start() {
        server = MockWebServer()
        server.start()
    }

    @After
    fun stop() = server.shutdown()

    /**
     * Mirrors the app's interceptor. Kept as a copy on purpose: the real one lives behind
     * Android-only types (encrypted prefs, BuildConfig), and the thing worth pinning is the
     * decision, not the plumbing.
     */
    private fun client(token: String?, sharedKey: String): OkHttpClient = OkHttpClient.Builder()
        .addInterceptor { chain ->
            val request = chain.request()
            val path = request.url.encodedPath
            val credential = when {
                path.trimStart('/') == "internal/logs" -> null
                BridgeConfig.isSharedKeyRoute(path) -> sharedKey.ifEmpty { null }
                else -> token
            }
            chain.proceed(
                if (credential == null) request
                else request.newBuilder().header("Authorization", "Bearer $credential").build()
            )
        }
        .build()

    private fun authHeaderFor(path: String, token: String? = "account-token", sharedKey: String = "shared-key"): String? {
        server.enqueue(MockResponse().setResponseCode(200))
        client(token, sharedKey)
            .newCall(Request.Builder().url(server.url(path)).build())
            .execute()
            .close()
        return server.takeRequest().getHeader("Authorization")
    }

    @Test
    fun `cloud artwork carries the account token`() {
        assertEquals("Bearer account-token", authHeaderFor("/user/music/artwork?path=a%2Fb.mp3"))
    }

    @Test
    fun `cloud stream carries the account token`() {
        assertEquals("Bearer account-token", authHeaderFor("/user/music/stream?path=a%2Fb.mp3"))
    }

    @Test
    fun `the legacy yt-dlp routes carry the shared key, not the account token`() {
        assertEquals("Bearer shared-key", authHeaderFor("/api/search"))
        assertEquals("Bearer shared-key", authHeaderFor("/api/stream/proxy"))
    }

    @Test
    fun `JWT-gated routes under the api prefix still get the account token`() {
        // `/api/` is not a rule: artist bio and the whole social family are JWT-gated.
        assertEquals("Bearer account-token", authHeaderFor("/api/artist/bio"))
        assertEquals("Bearer account-token", authHeaderFor("/api/social/friends"))
        assertFalse(BridgeConfig.isSharedKeyRoute("/api/artist/bio"))
    }

    @Test
    fun `log ingest sends no credential at all`() {
        assertNull(authHeaderFor("/internal/logs"))
    }

    @Test
    fun `a signed-out client simply sends nothing`() {
        assertNull(authHeaderFor("/user/music/artwork?path=a.mp3", token = null))
    }

    @Test
    fun `a self-hosted bridge with no key of its own sends no shared key`() {
        assertNull(authHeaderFor("/api/search", sharedKey = ""))
    }

    @Test
    fun `the official bridge is recognised however it is written`() {
        val official = BridgeConfig.DEFAULT_URL
        assertTrue(BridgeConfig.isOfficialBridge(official))
        assertTrue(BridgeConfig.isOfficialBridge("$official/"))
        assertTrue(BridgeConfig.isOfficialBridge(official.uppercase().replace("HTTPS", "https")))
        assertFalse(BridgeConfig.isOfficialBridge("https://someone-elses-bridge.example.com"))
    }
}

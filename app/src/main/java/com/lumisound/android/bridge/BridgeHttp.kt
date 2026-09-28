package com.lumisound.android.bridge

import com.google.gson.Gson
import com.google.gson.GsonBuilder
import com.lumisound.android.BuildConfig
import com.lumisound.android.bridge.api.AuthApi
import com.lumisound.android.bridge.api.CloudMusicApi
import com.lumisound.android.bridge.api.LibraryDataApi
import okhttp3.HttpUrl
import okhttp3.Interceptor
import okhttp3.OkHttpClient
import okhttp3.Response
import okhttp3.logging.HttpLoggingInterceptor
import retrofit2.Retrofit
import retrofit2.converter.gson.GsonConverterFactory
import java.util.concurrent.TimeUnit

/**
 * One OkHttp client and one Retrofit instance for the whole app, rebuilt only
 * when the base URL changes.
 *
 * The auth interceptor is the single place a credential is attached, so there is
 * exactly one implementation of "which credential does this route want" -- the
 * mistake that cost Lumisound every stream once was a hand-built header missing
 * the `"Bearer "` prefix, and a lone interceptor is how that stays impossible.
 */
class BridgeHttp(
    private val config: BridgeConfig,
    private val tokenStore: TokenStore,
    /** Populated from the stream response's `X-Loudness-Gain-Db` header. */
    private val loudnessStore: LoudnessGainStore,
) {

    val gson: Gson = GsonBuilder().setLenient().create()

    /**
     * Shared with ExoPlayer and Coil so stream/artwork requests get the same
     * credential handling (and connection pool) as the JSON calls.
     */
    val client: OkHttpClient = OkHttpClient.Builder()
        .connectTimeout(20, TimeUnit.SECONDS)
        .readTimeout(60, TimeUnit.SECONDS)
        .writeTimeout(120, TimeUnit.SECONDS)
        .retryOnConnectionFailure(true)
        .addInterceptor(AuthInterceptor(config, tokenStore))
        .addNetworkInterceptor(LoudnessHeaderInterceptor(loudnessStore))
        .apply {
            if (BuildConfig.DEBUG) {
                // BASIC, never HEADERS: the Authorization value must not reach logcat.
                addInterceptor(HttpLoggingInterceptor().setLevel(HttpLoggingInterceptor.Level.BASIC))
            }
        }
        .build()

    @Volatile
    private var retrofitFor: String = ""

    @Volatile
    private var retrofit: Retrofit = build()

    private fun build(): Retrofit {
        val base = BridgeConfig.normalise(config.baseUrl) + "/"
        retrofitFor = base
        return Retrofit.Builder()
            .baseUrl(base)
            .client(client)
            .addConverterFactory(GsonConverterFactory.create(gson))
            .build()
    }

    private fun current(): Retrofit {
        val wanted = BridgeConfig.normalise(config.baseUrl) + "/"
        if (wanted != retrofitFor) {
            synchronized(this) {
                if (wanted != retrofitFor) retrofit = build()
            }
        }
        return retrofit
    }

    val auth: AuthApi get() = current().create(AuthApi::class.java)
    val cloudMusic: CloudMusicApi get() = current().create(CloudMusicApi::class.java)
    val libraryData: LibraryDataApi get() = current().create(LibraryDataApi::class.java)
}

/**
 * Attaches the right credential for the route: the shared bridge key on
 * `check_auth()`-gated legacy routes, the account session token everywhere else.
 * A request that already carries an Authorization header (the login call itself,
 * or a stream URL built with its own) is left alone.
 */
private class AuthInterceptor(
    private val config: BridgeConfig,
    private val tokenStore: TokenStore,
) : Interceptor {
    override fun intercept(chain: Interceptor.Chain): Response {
        val request = chain.request()
        if (request.header("Authorization") != null) return chain.proceed(request)

        val credential = if (BridgeConfig.isSharedKeyRoute(request.url.encodedPath)) {
            config.sharedApiKey
        } else {
            tokenStore.token
        }
        if (credential.isNullOrEmpty()) return chain.proceed(request)

        return chain.proceed(
            request.newBuilder()
                .header("Authorization", "Bearer $credential")
                .build()
        )
    }
}

/**
 * Captures `X-Loudness-Gain-Db` off a `/user/music/stream` response. The server
 * computes it from the file's analyzed LUFS, and it is the only place that value
 * is ever published -- the track listing does not carry it -- so it has to be
 * read from the playback response itself as a side effect.
 */
private class LoudnessHeaderInterceptor(
    private val store: LoudnessGainStore,
) : Interceptor {
    override fun intercept(chain: Interceptor.Chain): Response {
        val response = chain.proceed(chain.request())
        val gain = response.header("X-Loudness-Gain-Db")?.toFloatOrNull()
        if (gain != null) {
            store.put(chain.request().url.serverPathParam(), gain)
        }
        return response
    }
}

private fun HttpUrl.serverPathParam(): String = queryParameter("path") ?: encodedPath

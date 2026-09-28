package com.lumisound.android.bridge

import android.content.Context
import android.util.Log
import androidx.security.crypto.EncryptedSharedPreferences
import androidx.security.crypto.MasterKey

/**
 * The account session token, kept in EncryptedSharedPreferences -- Android's
 * nearest equivalent to the iOS Keychain that Lumisound's `KeychainTokenStore`
 * uses for the same value. Never logged: the token IS the account for 30 days.
 */
class TokenStore(context: Context) {

    private val prefs = try {
        EncryptedSharedPreferences.create(
            context,
            FILE,
            MasterKey.Builder(context).setKeyScheme(MasterKey.KeyScheme.AES256_GCM).build(),
            EncryptedSharedPreferences.PrefKeyEncryptionScheme.AES256_SIV,
            EncryptedSharedPreferences.PrefValueEncryptionScheme.AES256_GCM,
        )
    } catch (e: Exception) {
        // A corrupt keystore entry (seen after some OS upgrades / restores) makes
        // create() throw forever. Wiping the file is the documented recovery and
        // costs the user one sign-in, where the alternative is an app that cannot
        // start at all.
        Log.w(TAG, "secure prefs unusable, recreating: ${e.javaClass.simpleName}")
        context.deleteSharedPreferences(FILE)
        EncryptedSharedPreferences.create(
            context,
            FILE,
            MasterKey.Builder(context).setKeyScheme(MasterKey.KeyScheme.AES256_GCM).build(),
            EncryptedSharedPreferences.PrefKeyEncryptionScheme.AES256_SIV,
            EncryptedSharedPreferences.PrefValueEncryptionScheme.AES256_GCM,
        )
    }

    @Volatile
    private var cached: String? = prefs.getString(KEY_TOKEN, null)

    @Volatile
    private var cachedUserId: String? = cached?.let(::subjectOf)

    /** Read on every request from the OkHttp interceptor, hence the memory cache. */
    val token: String? get() = cached

    /**
     * The account id the current token belongs to, read out of the token's own
     * payload. Telemetry needs it to attribute a log line, and asking the server
     * for something already sitting in the credential would be silly -- no
     * signature check is involved or needed, since this is only ever used as a
     * label for logs the server re-derives anyway.
     */
    val userId: String? get() = cachedUserId

    fun save(token: String) {
        cached = token
        cachedUserId = subjectOf(token)
        prefs.edit().putString(KEY_TOKEN, token).apply()
    }

    fun clear() {
        cached = null
        cachedUserId = null
        prefs.edit().remove(KEY_TOKEN).apply()
    }

    private fun subjectOf(jwt: String): String? = try {
        val payload = jwt.split('.').getOrNull(1) ?: return null
        val json = String(
            android.util.Base64.decode(payload, android.util.Base64.URL_SAFE or android.util.Base64.NO_PADDING),
            Charsets.UTF_8,
        )
        Regex("\"sub\"\\s*:\\s*\"([^\"]+)\"").find(json)?.groupValues?.get(1)
    } catch (e: Exception) {
        null
    }

    private companion object {
        const val TAG = "LumiTokenStore"
        const val FILE = "lumimusic_secure_prefs"
        const val KEY_TOKEN = "account_token"
    }
}

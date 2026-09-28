package com.lumisound.android.bridge

import android.content.Context
import android.content.SharedPreferences
import android.util.Log
import androidx.security.crypto.EncryptedSharedPreferences
import androidx.security.crypto.MasterKey

/**
 * The account session token, kept in EncryptedSharedPreferences -- Android's nearest
 * equivalent to the iOS Keychain that Lumisound's `KeychainTokenStore` uses for the same
 * value. Never logged: for thirty days this token IS the account.
 */
class TokenStore(context: Context) {

    /**
     * Null when this device cannot provide encrypted storage at all.
     *
     * That is not hypothetical: a device with no AndroidKeyStore provider (some custom
     * ROMs, and every JVM-side test environment) previously took the whole app down at
     * launch, since building the master key threw straight out of `Application.onCreate`.
     * When it happens the token is held in memory for the session instead of being written
     * anywhere -- sign-in is needed again next launch, which is a far better outcome than
     * either an app that cannot start or a session token sitting in plaintext on disk.
     */
    private val prefs: SharedPreferences? = openSecurely(context)

    @Volatile
    private var cached: String? = prefs?.getString(KEY_TOKEN, null)

    @Volatile
    private var cachedUserId: String? = cached?.let(::subjectOf)

    /** True when the token survives a restart; false when storage was unavailable. */
    val isPersistent: Boolean get() = prefs != null

    /** Read on every request from the OkHttp interceptor, hence the memory cache. */
    val token: String? get() = cached

    /**
     * The account id the current token belongs to, read out of the token's own payload.
     * Telemetry needs it to attribute a log line, and asking the server for something
     * already sitting in the credential would be silly -- no signature check is involved or
     * needed, since this is only ever a label for logs the server re-derives anyway.
     */
    val userId: String? get() = cachedUserId

    fun save(token: String) {
        cached = token
        cachedUserId = subjectOf(token)
        prefs?.edit()?.putString(KEY_TOKEN, token)?.apply()
    }

    fun clear() {
        cached = null
        cachedUserId = null
        prefs?.edit()?.remove(KEY_TOKEN)?.apply()
    }

    private fun openSecurely(context: Context): SharedPreferences? = try {
        create(context)
    } catch (e: Exception) {
        // A corrupt keystore entry (seen after some OS upgrades and restores) makes create()
        // throw forever. Wiping the file is the documented recovery and costs one sign-in.
        Log.w(TAG, "secure prefs unusable, recreating: ${e.javaClass.simpleName}")
        try {
            context.deleteSharedPreferences(FILE)
            create(context)
        } catch (e2: Exception) {
            Log.w(TAG, "no encrypted storage on this device; session will not persist: ${e2.javaClass.simpleName}")
            null
        }
    }

    private fun create(context: Context): SharedPreferences = EncryptedSharedPreferences.create(
        context,
        FILE,
        MasterKey.Builder(context).setKeyScheme(MasterKey.KeyScheme.AES256_GCM).build(),
        EncryptedSharedPreferences.PrefKeyEncryptionScheme.AES256_SIV,
        EncryptedSharedPreferences.PrefValueEncryptionScheme.AES256_GCM,
    )

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

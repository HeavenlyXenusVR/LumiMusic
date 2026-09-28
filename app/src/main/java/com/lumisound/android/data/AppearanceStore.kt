package com.lumisound.android.data

import android.content.Context
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow

/**
 * The account's accent colour, persisted.
 *
 * `theme_color` is the one genuinely cross-platform field in the shared settings row, so
 * the two apps can look like the same account. It was previously held only in memory by
 * the importer, which meant the colour was correct until the app was closed and then
 * silently reverted to the default on every launch until another import ran.
 */
class AppearanceStore(context: Context) {

    private val prefs = context.getSharedPreferences("lumimusic_appearance", Context.MODE_PRIVATE)

    private val _accent = MutableStateFlow(prefs.getString(KEY_ACCENT, null))
    val accent: StateFlow<String?> = _accent.asStateFlow()

    fun setAccent(hex: String?) {
        val cleaned = hex?.trim()?.takeIf { it.startsWith("#") && (it.length == 7 || it.length == 9) }
        if (cleaned == _accent.value) return
        _accent.value = cleaned
        prefs.edit().apply { if (cleaned == null) remove(KEY_ACCENT) else putString(KEY_ACCENT, cleaned) }.apply()
    }

    /** Sign-out drops it: the next account's colour should not be the previous one's. */
    fun clear() = setAccent(null)

    private companion object {
        const val KEY_ACCENT = "accent_hex"
    }
}

package com.lumisound.android.data

import android.content.Context
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow

/**
 * The last few things searched for, newest first, kept on this device only.
 *
 * Deliberately local: the bridge already logs queries for its trending list, and a
 * personal history of what someone typed has no business being synced anywhere else.
 */
class SearchHistoryStore(context: Context) {

    private val prefs = context.getSharedPreferences("lumimusic_search", Context.MODE_PRIVATE)

    private val _recent = MutableStateFlow(load())
    val recent: StateFlow<List<String>> = _recent.asStateFlow()

    fun record(query: String) {
        val cleaned = query.trim()
        if (cleaned.isEmpty()) return
        val next = (listOf(cleaned) + _recent.value.filterNot { it.equals(cleaned, ignoreCase = true) }).take(MAX)
        _recent.value = next
        // A newline cannot be typed into the single-line field, so it is a safe separator.
        prefs.edit().putString(KEY, next.joinToString("\n")).apply()
    }

    fun clear() {
        _recent.value = emptyList()
        prefs.edit().remove(KEY).apply()
    }

    private fun load(): List<String> =
        prefs.getString(KEY, null)?.split("\n")?.filter { it.isNotBlank() }.orEmpty()

    private companion object {
        const val KEY = "recent_queries"
        const val MAX = 12
    }
}

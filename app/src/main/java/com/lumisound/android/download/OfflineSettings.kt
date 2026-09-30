package com.lumisound.android.download

import android.content.Context
import android.content.Intent
import android.net.Uri
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update

data class OfflinePrefs(
    /** Keep a copy of every cloud track on the phone, downloading new ones as they appear. */
    val downloadWholeLibrary: Boolean = false,
    /** Only transfer on an unmetered network. On by default: a library is gigabytes. */
    val wifiOnly: Boolean = true,
    /** A folder picked in the system picker, or null for the phone folder. */
    val folderUri: String? = null,
)

class OfflineSettings(private val context: Context) {

    private val prefs = context.getSharedPreferences("lumimusic_offline", Context.MODE_PRIVATE)

    private val _state = MutableStateFlow(
        OfflinePrefs(
            downloadWholeLibrary = prefs.getBoolean(KEY_WHOLE_LIBRARY, false),
            wifiOnly = prefs.getBoolean(KEY_WIFI_ONLY, true),
            folderUri = prefs.getString(KEY_FOLDER, null),
        )
    )
    val state: StateFlow<OfflinePrefs> = _state.asStateFlow()

    fun setDownloadWholeLibrary(on: Boolean) = edit { it.copy(downloadWholeLibrary = on) }

    fun setWifiOnly(on: Boolean) = edit { it.copy(wifiOnly = on) }

    /**
     * Takes the persistable grant for a folder from the system picker, so it survives
     * restarts; the grant on the previous folder, if any, is released.
     */
    fun setFolder(uri: Uri?) {
        val previous = _state.value.folderUri
        if (uri != null) {
            context.contentResolver.takePersistableUriPermission(
                uri,
                Intent.FLAG_GRANT_READ_URI_PERMISSION or Intent.FLAG_GRANT_WRITE_URI_PERMISSION,
            )
        }
        if (previous != null && previous != uri?.toString()) {
            runCatching {
                context.contentResolver.releasePersistableUriPermission(
                    Uri.parse(previous),
                    Intent.FLAG_GRANT_READ_URI_PERMISSION or Intent.FLAG_GRANT_WRITE_URI_PERMISSION,
                )
            }
        }
        edit { it.copy(folderUri = uri?.toString()) }
    }

    /** The storage new downloads go to. A chosen folder that is no longer writable falls back to the phone folder. */
    fun storage(): OfflineStorage {
        val chosen = _state.value.folderUri?.let { OfflineStorage.ChosenFolder(context, Uri.parse(it)) }
        return chosen?.takeIf { it.isUsable } ?: OfflineStorage.phoneFolder(context)
    }

    private fun edit(change: (OfflinePrefs) -> OfflinePrefs) {
        _state.update(change)
        val s = _state.value
        prefs.edit()
            .putBoolean(KEY_WHOLE_LIBRARY, s.downloadWholeLibrary)
            .putBoolean(KEY_WIFI_ONLY, s.wifiOnly)
            .putString(KEY_FOLDER, s.folderUri)
            .apply()
    }

    private companion object {
        const val KEY_WHOLE_LIBRARY = "whole_library"
        const val KEY_WIFI_ONLY = "wifi_only"
        const val KEY_FOLDER = "folder_uri"
    }
}

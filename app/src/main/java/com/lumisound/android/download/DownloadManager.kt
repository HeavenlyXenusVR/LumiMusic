package com.lumisound.android.download

import android.content.Context
import com.lumisound.android.bridge.BridgeConfig
import com.lumisound.android.bridge.BridgeHttp
import com.lumisound.android.bridge.BridgeUrls
import com.lumisound.android.data.db.CloudTrackEntity
import com.lumisound.android.data.db.DownloadEntity
import com.lumisound.android.data.db.LumiDatabase
import com.lumisound.android.diagnostics.AppLogger
import com.lumisound.android.diagnostics.RemoteLogger
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import okhttp3.Request
import java.io.File

/**
 * Saves cloud tracks for offline playback.
 *
 * The bytes are written exactly as the server sent them, which for a locked track
 * means the file on disk stays masked -- the player's data source unmasks it on the
 * way to the decoder, the same as when streaming. Writing an unmasked copy would
 * hand every other app on the device a playable file, which is the one thing the
 * format exists to prevent.
 *
 * One download at a time, sequentially: this is a phone on someone's mobile
 * connection, and eight parallel transfers of a lossless album is how a library sync
 * becomes the reason the app is uninstalled.
 */
class DownloadManager(
    context: Context,
    private val http: BridgeHttp,
    private val config: BridgeConfig,
    private val database: LumiDatabase,
    private val remote: RemoteLogger,
    private val scope: CoroutineScope,
) {

    data class Progress(
        val serverPath: String,
        val title: String,
        val bytesRead: Long,
        val totalBytes: Long,
    ) {
        val fraction: Float get() = if (totalBytes <= 0) 0f else (bytesRead.toFloat() / totalBytes).coerceIn(0f, 1f)
    }

    private val directory = File(context.filesDir, "offline").apply { mkdirs() }

    private val _active = MutableStateFlow<Progress?>(null)
    val active: StateFlow<Progress?> = _active.asStateFlow()

    private val _queued = MutableStateFlow<List<String>>(emptyList())
    val queued: StateFlow<List<String>> = _queued.asStateFlow()

    private val pending = ArrayDeque<CloudTrackEntity>()

    @Volatile
    private var draining = false

    fun enqueue(tracks: List<CloudTrackEntity>) {
        synchronized(pending) {
            val known = pending.map { it.serverPath }.toSet()
            tracks.forEach { if (it.serverPath !in known) pending.addLast(it) }
            _queued.value = pending.map { it.serverPath }
        }
        drain()
    }

    fun cancelAll() {
        synchronized(pending) {
            pending.clear()
            _queued.value = emptyList()
        }
    }

    suspend fun remove(serverPath: String) = withContext(Dispatchers.IO) {
        val row = database.downloads().byPath(serverPath) ?: return@withContext
        runCatching { File(row.localPath).delete() }
        database.downloads().delete(serverPath)
    }

    suspend fun isDownloaded(serverPath: String): String? =
        database.downloads().byPath(serverPath)?.localPath?.takeIf { File(it).exists() }

    private fun drain() {
        if (draining) return
        draining = true
        scope.launch(Dispatchers.IO) {
            try {
                while (true) {
                    val next = synchronized(pending) {
                        pending.removeFirstOrNull().also { _queued.value = pending.map { p -> p.serverPath } }
                    } ?: break
                    download(next)
                }
            } finally {
                draining = false
                _active.value = null
            }
        }
    }

    private suspend fun download(track: CloudTrackEntity) {
        if (isDownloaded(track.serverPath) != null) return
        val url = BridgeUrls.stream(config.baseUrl, track.serverPath, track.isLocked)
        // A partial file must never be mistaken for a finished one, so the download
        // lands on a temp name and is renamed only after the stream closes cleanly.
        val target = File(directory, safeName(track))
        val temp = File(directory, "${safeName(track)}.part")
        try {
            http.client.newCall(Request.Builder().url(url).build()).execute().use { response ->
                if (!response.isSuccessful) {
                    AppLogger.w("download", "HTTP ${response.code} for ${track.serverPath}")
                    remote.log("download", "failed", level = "warn", message = "HTTP ${response.code}")
                    return
                }
                val body = response.body ?: return
                val total = body.contentLength()
                _active.value = Progress(track.serverPath, track.title, 0, total)
                body.byteStream().use { input ->
                    temp.outputStream().use { output ->
                        val buffer = ByteArray(64 * 1024)
                        var read: Int
                        var sum = 0L
                        var lastPublished = 0L
                        while (true) {
                            read = input.read(buffer)
                            if (read <= 0) break
                            output.write(buffer, 0, read)
                            sum += read
                            // Publishing every chunk would recompose the UI hundreds of
                            // times a second for no visible benefit.
                            if (sum - lastPublished > 256 * 1024) {
                                lastPublished = sum
                                _active.value = Progress(track.serverPath, track.title, sum, total)
                            }
                        }
                    }
                }
            }
            if (!temp.renameTo(target)) {
                temp.copyTo(target, overwrite = true)
                temp.delete()
            }
            database.downloads().upsert(
                DownloadEntity(
                    serverPath = track.serverPath,
                    localPath = target.absolutePath,
                    isLocked = track.isLocked,
                    sizeBytes = target.length(),
                    completedAt = System.currentTimeMillis(),
                )
            )
            AppLogger.i("download", "saved ${track.serverPath} (${target.length()} bytes)")
        } catch (e: Exception) {
            temp.delete()
            AppLogger.e("download", "failed for ${track.serverPath}", e)
            remote.log("download", "failed", level = "error", message = e.message)
        }
    }

    /** Path-derived, so two tracks with the same title in different folders can't collide. */
    private fun safeName(track: CloudTrackEntity): String =
        track.serverPath.replace(Regex("[^A-Za-z0-9._-]"), "_").takeLast(120)
}

package com.lumisound.android.download

import android.content.Context
import android.net.ConnectivityManager
import android.net.Uri
import androidx.documentfile.provider.DocumentFile
import com.lumisound.android.BuildConfig
import com.lumisound.android.bridge.BridgeConfig
import com.lumisound.android.bridge.BridgeHttp
import com.lumisound.android.bridge.BridgeUrls
import com.lumisound.android.data.db.CloudTrackEntity
import com.lumisound.android.data.db.DownloadEntity
import com.lumisound.android.data.db.LumiDatabase
import com.lumisound.android.diagnostics.AppLogger
import com.lumisound.android.diagnostics.RemoteLogger
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import kotlinx.coroutines.withContext
import okhttp3.Request
import java.io.File
import java.io.IOException
import java.time.Instant

/**
 * Saves cloud tracks to the phone: one at a time from the library, or the whole library
 * via [syncLibrary] (driven by `LibraryDownloadWorker`).
 *
 * Each track is saved as `Artist/Album/NN Title.ext` in the offline folder (see
 * [OfflineStorage]), with its cover as `.jpg` and its metadata as `.json` beside it
 * ([TrackFiles]).
 *
 * The audio bytes are written exactly as the server sent them, which for a locked track
 * means the file on disk stays masked -- the player's data source unmasks it on the way
 * to the decoder, the same as when streaming. Writing an unmasked copy would hand every
 * other app on the device a playable file, which is the one thing the format exists to
 * prevent.
 *
 * One transfer at a time across both paths: this is a phone, often on someone's mobile
 * connection, and parallel transfers of a lossless library is how a sync becomes the
 * reason the app is uninstalled.
 */
class DownloadManager(
    private val context: Context,
    private val http: BridgeHttp,
    private val config: BridgeConfig,
    private val database: LumiDatabase,
    private val remote: RemoteLogger,
    private val scope: CoroutineScope,
    val settings: OfflineSettings = OfflineSettings(context),
) {

    data class Progress(
        val serverPath: String,
        val title: String,
        val bytesRead: Long,
        val totalBytes: Long,
    ) {
        val fraction: Float get() = if (totalBytes <= 0) 0f else (bytesRead.toFloat() / totalBytes).coerceIn(0f, 1f)
    }

    /** The whole-library download, as the Offline screen and the notification show it. */
    data class LibrarySync(
        val running: Boolean = false,
        /** Tracks in the cloud library. */
        val total: Int = 0,
        /** Of those, on the phone. */
        val saved: Int = 0,
        val failed: Int = 0,
        /** Why a run stopped short, in words: "Waiting for Wi-Fi", "Storage full". */
        val note: String? = null,
        val finishedAt: Long? = null,
    )

    private sealed interface Outcome {
        data object Saved : Outcome
        data object Skipped : Outcome
        data class Failed(val reason: String, val fatal: Boolean = false) : Outcome
    }

    private val transferLock = Mutex()

    private val _active = MutableStateFlow<Progress?>(null)
    val active: StateFlow<Progress?> = _active.asStateFlow()

    private val _queued = MutableStateFlow<List<String>>(emptyList())
    val queued: StateFlow<List<String>> = _queued.asStateFlow()

    private val _library = MutableStateFlow(LibrarySync())
    val library: StateFlow<LibrarySync> = _library.asStateFlow()

    private val pending = ArrayDeque<CloudTrackEntity>()

    /** Where downloads made before the phone folder existed were kept. */
    private val legacyDirectory = File(context.filesDir, "offline")

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

    /** Fills in the library counts for the Offline screen when no run is going. */
    suspend fun refreshCounts() = withContext(Dispatchers.IO) {
        if (_library.value.running) return@withContext
        val total = database.cloudTracks().count()
        val saved = database.downloads().count().coerceAtMost(total)
        _library.update { it.copy(total = total, saved = saved) }
    }

    fun cancelAll() {
        synchronized(pending) {
            pending.clear()
            _queued.value = emptyList()
        }
    }

    suspend fun remove(serverPath: String) = withContext(Dispatchers.IO) {
        val row = database.downloads().byPath(serverPath) ?: return@withContext
        val storage = storageFor(row.localPath)
        siblingsOf(row.localPath, database.cloudTracks().byPath(serverPath), storage).forEach { runCatching { storage.delete(it) } }
        runCatching { storage.delete(row.localPath) }
        database.downloads().delete(serverPath)
    }

    /**
     * The saved copy's location if there is one. A file path is checked on disk; a chosen
     * folder's `content://` entry is trusted, since checking it is a provider query per
     * track and this is asked for every row of a list.
     */
    suspend fun isDownloaded(serverPath: String): String? {
        val location = database.downloads().byPath(serverPath)?.localPath ?: return null
        return if (location.startsWith("content://") || File(location).exists()) location else null
    }

    /**
     * Downloads every cloud track not yet on the phone, newest library listing first
     * refreshed by the caller. Stops early when [shouldStop] says so (the worker was
     * cancelled), on a metered network while Wi-Fi only is on, or when storage runs out.
     *
     * Returns true when every track is on the phone.
     */
    suspend fun syncLibrary(shouldStop: () -> Boolean = { false }, onProgress: (LibrarySync) -> Unit = {}): Boolean =
        withContext(Dispatchers.IO) {
            val tracks = database.cloudTracks().all()
            val rows = database.downloads().paths().toHashSet()
            val storage = settings.storage()
            var saved = tracks.count { it.serverPath in rows }
            var failed = 0
            fun publish(note: String? = null, running: Boolean = true) {
                _library.value = LibrarySync(running, tracks.size, saved, failed, note, if (running) null else System.currentTimeMillis())
                onProgress(_library.value)
            }
            publish()
            try {
                for (track in tracks) {
                    if (shouldStop()) return@withContext false.also { publish("Paused", running = false) }
                    if (settings.state.value.wifiOnly && isMetered()) {
                        return@withContext false.also { publish("Waiting for Wi-Fi", running = false) }
                    }
                    val existing = database.downloads().byPath(track.serverPath)
                    val outcome = when {
                        existing == null -> transfer(track, storage)
                        // Saved before downloads went to a visible folder: moved rather than refetched.
                        existing.localPath.startsWith(legacyDirectory.absolutePath) -> relocate(existing, track, storage)
                        else -> Outcome.Skipped
                    }
                    when (outcome) {
                        Outcome.Saved -> if (existing == null) saved++
                        Outcome.Skipped -> Unit
                        is Outcome.Failed -> {
                            failed++
                            if (outcome.fatal) return@withContext false.also { publish(outcome.reason, running = false) }
                        }
                    }
                    publish()
                }
                publish(if (failed > 0) "$failed couldn't be saved; they'll be retried" else null, running = false)
                failed == 0
            } catch (e: CancellationException) {
                publish("Paused", running = false)
                throw e
            }
        }

    private fun drain() {
        if (draining) return
        draining = true
        scope.launch(Dispatchers.IO) {
            try {
                val storage = settings.storage()
                while (true) {
                    val next = synchronized(pending) {
                        pending.removeFirstOrNull().also { _queued.value = pending.map { p -> p.serverPath } }
                    } ?: break
                    if (isDownloaded(next.serverPath) == null) transfer(next, storage)
                }
            } finally {
                draining = false
                _active.value = null
            }
        }
    }

    /** Audio, then cover, then metadata; the row is written last, so it only ever points at a whole file. */
    private suspend fun transfer(track: CloudTrackEntity, storage: OfflineStorage): Outcome = transferLock.withLock {
        try {
            val folders = TrackFiles.folders(track)
            val base = freeBaseName(track, storage, folders)
            val audioName = "$base.${TrackFiles.extension(track)}"
            val url = BridgeUrls.stream(config.baseUrl, track.serverPath, track.isLocked)
            val location = http.client.newCall(Request.Builder().url(url).build()).execute().use { response ->
                if (!response.isSuccessful) {
                    AppLogger.w("download", "HTTP ${response.code} for ${track.serverPath}")
                    remote.log("download", "failed", level = "warn", message = "HTTP ${response.code}")
                    return Outcome.Failed("HTTP ${response.code}", fatal = response.code == 401 || response.code == 403)
                }
                val body = response.body ?: return Outcome.Failed("Empty response")
                val total = body.contentLength()
                _active.value = Progress(track.serverPath, track.title, 0, total)
                storage.save(folders, audioName, mimeFor(track)) { output ->
                    body.byteStream().use { input ->
                        val buffer = ByteArray(64 * 1024)
                        var sum = 0L
                        var lastPublished = 0L
                        while (true) {
                            val read = input.read(buffer)
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
            val artwork = saveArtwork(track, storage, folders, base)
            saveSidecar(track, storage, folders, base, artwork)
            database.downloads().upsert(
                DownloadEntity(
                    serverPath = track.serverPath,
                    localPath = location,
                    isLocked = track.isLocked,
                    sizeBytes = sizeOf(location),
                    completedAt = System.currentTimeMillis(),
                )
            )
            AppLogger.i("download", "saved ${track.serverPath}")
            Outcome.Saved
        } catch (e: CancellationException) {
            throw e
        } catch (e: Exception) {
            AppLogger.e("download", "failed for ${track.serverPath}", e)
            remote.log("download", "failed", level = "error", message = e.message)
            val full = e is IOException && e.message?.contains("ENOSPC") == true
            Outcome.Failed(if (full) "Storage full" else (e.message ?: "Download failed"), fatal = full)
        } finally {
            _active.value = null
        }
    }

    /** A download from before the visible folder: copied across with its metadata, then the old copy removed. */
    private suspend fun relocate(row: DownloadEntity, track: CloudTrackEntity, storage: OfflineStorage): Outcome {
        val old = File(row.localPath)
        if (!old.isFile) {
            // The old copy is gone (cleared storage): fetch it again. Outside the lock,
            // which transfer takes itself.
            database.downloads().delete(row.serverPath)
            return transfer(track, storage)
        }
        return transferLock.withLock { moveLegacy(old, row, track, storage) }
    }

    private suspend fun moveLegacy(old: File, row: DownloadEntity, track: CloudTrackEntity, storage: OfflineStorage): Outcome {
        return try {
            val folders = TrackFiles.folders(track)
            val base = freeBaseName(track, storage, folders)
            val location = storage.save(folders, "$base.${TrackFiles.extension(track)}", mimeFor(track)) { output ->
                old.inputStream().use { it.copyTo(output) }
            }
            val artwork = saveArtwork(track, storage, folders, base)
            saveSidecar(track, storage, folders, base, artwork)
            database.downloads().upsert(row.copy(localPath = location, sizeBytes = sizeOf(location)))
            old.delete()
            Outcome.Saved
        } catch (e: CancellationException) {
            throw e
        } catch (e: Exception) {
            AppLogger.e("download", "couldn't move ${track.serverPath}", e)
            Outcome.Failed(e.message ?: "Move failed")
        }
    }

    /** The cover as `base.jpg`, if the bridge has one; its absence is not a failure. */
    private fun saveArtwork(track: CloudTrackEntity, storage: OfflineStorage, folders: List<String>, base: String): String? {
        val name = "$base.jpg"
        return try {
            http.client.newCall(Request.Builder().url(BridgeUrls.artwork(config.baseUrl, track.serverPath)).build()).execute().use { response ->
                val bytes = response.takeIf { it.isSuccessful }?.body?.bytes()?.takeIf { it.isNotEmpty() } ?: return null
                storage.save(folders, name, "image/jpeg") { it.write(bytes) }
                name
            }
        } catch (e: IOException) {
            AppLogger.w("download", "no cover saved for ${track.serverPath}: ${e.message}")
            null
        }
    }

    private fun saveSidecar(track: CloudTrackEntity, storage: OfflineStorage, folders: List<String>, base: String, artwork: String?) {
        val json = TrackFiles.sidecarJson(track, artwork, Instant.now().toString(), BuildConfig.VERSION_NAME)
        storage.save(folders, "$base.json", "application/json") { it.write(json.toByteArray()) }
    }

    /**
     * The track's base name, with a short tag added only if another track's download
     * already has that name in this album folder.
     */
    private suspend fun freeBaseName(track: CloudTrackEntity, storage: OfflineStorage, folders: List<String>): String {
        val plain = TrackFiles.baseName(track)
        val taken = storage.find(folders, "$plain.${TrackFiles.extension(track)}") ?: return plain
        val owner = database.downloads().byLocalPath(taken)
        return if (owner == null || owner.serverPath == track.serverPath) plain
        else TrackFiles.baseName(track, TrackFiles.shortHash(track.serverPath))
    }

    /** The `.json` and `.jpg` saved beside a download. */
    private fun siblingsOf(location: String, track: CloudTrackEntity?, storage: OfflineStorage): List<String> {
        if (!location.startsWith("content://")) {
            val file = File(location)
            val base = file.nameWithoutExtension
            return listOf("$base.json", "$base.jpg").map { File(file.parentFile, it) }.filter { it.isFile }.map { it.absolutePath }
        }
        track ?: return emptyList()
        val name = DocumentFile.fromSingleUri(context, Uri.parse(location))?.name ?: return emptyList()
        val base = name.substringBeforeLast('.')
        val folders = TrackFiles.folders(track)
        return listOfNotNull(storage.find(folders, "$base.json"), storage.find(folders, "$base.jpg"))
    }

    private fun storageFor(location: String): OfflineStorage =
        if (location.startsWith("content://")) settings.storage() else OfflineStorage.PhoneFolder(File(location).parentFile ?: legacyDirectory)

    private fun sizeOf(location: String): Long =
        if (location.startsWith("content://")) DocumentFile.fromSingleUri(context, Uri.parse(location))?.length() ?: 0L
        else File(location).length()

    private fun isMetered(): Boolean =
        (context.getSystemService(Context.CONNECTIVITY_SERVICE) as ConnectivityManager).isActiveNetworkMetered

    private fun mimeFor(track: CloudTrackEntity): String = when (TrackFiles.extension(track)) {
        "mp3" -> "audio/mpeg"
        "m4a", "aac", "alac" -> "audio/mp4"
        "flac" -> "audio/flac"
        "ogg", "opus" -> "audio/ogg"
        "wav" -> "audio/wav"
        else -> "application/octet-stream"
    }
}

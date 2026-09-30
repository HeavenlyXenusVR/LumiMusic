package com.lumisound.android.download

import android.content.Context
import android.net.Uri
import androidx.documentfile.provider.DocumentFile
import java.io.File
import java.io.OutputStream

/**
 * Where downloads are written. Two kinds:
 *
 * - [PhoneFolder]: `Android/media/com.lumisound.android/LumiMusic` on the phone's shared
 *   storage. No permission is needed, it shows up in the Files app and over USB, and
 *   the player reads it as ordinary files. Android removes it if the app is uninstalled.
 * - [ChosenFolder]: any folder picked in the system folder picker (Music, an SD card,
 *   ...). Written through the Storage Access Framework, and kept after an uninstall.
 *
 * Paths handed in are relative: `["Artist", "Album"]` plus a file name. What comes back
 * as a location is what goes into `DownloadEntity.localPath` -- an absolute file path
 * for the phone folder, a `content://` URI for a chosen one.
 */
sealed interface OfflineStorage {

    /** Shown in settings: "Phone › Android/media/…/LumiMusic" or the chosen folder's name. */
    val label: String

    /** The existing file's location, or null. */
    fun find(folders: List<String>, name: String): String?

    /**
     * Writes a file through a temporary name and renames it into place only after
     * [write] returns, so a download cut off halfway is never mistaken for a finished
     * one. Replaces any existing file of the same name. Returns the final location.
     */
    fun save(folders: List<String>, name: String, mimeType: String, write: (OutputStream) -> Unit): String

    fun exists(location: String): Boolean

    fun delete(location: String): Boolean

    class PhoneFolder(val root: File) : OfflineStorage {
        override val label: String get() = root.absolutePath.substringAfter("/0/", root.absolutePath)

        override fun find(folders: List<String>, name: String): String? =
            File(dir(folders), name).takeIf { it.isFile }?.absolutePath

        override fun save(folders: List<String>, name: String, mimeType: String, write: (OutputStream) -> Unit): String {
            val dir = dir(folders).apply { mkdirs() }
            val target = File(dir, name)
            val temp = File(dir, "$name.part")
            try {
                temp.outputStream().use(write)
                if (target.exists()) target.delete()
                if (!temp.renameTo(target)) {
                    temp.copyTo(target, overwrite = true)
                    temp.delete()
                }
            } catch (e: Exception) {
                temp.delete()
                throw e
            }
            return target.absolutePath
        }

        override fun exists(location: String) = File(location).isFile

        override fun delete(location: String) = File(location).delete()

        private fun dir(folders: List<String>) = folders.fold(root) { parent, segment -> File(parent, segment) }
    }

    class ChosenFolder(private val context: Context, val treeUri: Uri) : OfflineStorage {
        private val tree: DocumentFile? get() = DocumentFile.fromTreeUri(context, treeUri)

        // Walking a folder tree through the provider is a query per level; album
        // folders are looked up once per download run, not once per file.
        private val dirCache = HashMap<List<String>, DocumentFile>()

        override val label: String get() = tree?.name ?: "Chosen folder"

        /** False once the permission is revoked or the folder (or its SD card) is gone. */
        val isUsable: Boolean get() = tree?.let { it.canWrite() && it.isDirectory } == true

        override fun find(folders: List<String>, name: String): String? =
            dir(folders, create = false)?.findFile(name)?.takeIf { it.isFile }?.uri?.toString()

        override fun save(folders: List<String>, name: String, mimeType: String, write: (OutputStream) -> Unit): String {
            val dir = dir(folders, create = true) ?: error("Can't create ${folders.joinToString("/")} in the chosen folder")
            val tempName = "$name.part"
            dir.findFile(tempName)?.delete()
            // octet-stream so the provider keeps the name exactly as given rather than
            // appending an extension it thinks the type calls for.
            val temp = dir.createFile("application/octet-stream", tempName) ?: error("Can't create $name in the chosen folder")
            try {
                val stream = context.contentResolver.openOutputStream(temp.uri, "w") ?: error("Can't write $name")
                stream.use(write)
                dir.findFile(name)?.delete()
                if (!temp.renameTo(name)) error("Can't rename $tempName")
            } catch (e: Exception) {
                temp.delete()
                throw e
            }
            // renameTo can change the document's URI; look it up again by its final name.
            return (dir.findFile(name) ?: temp).uri.toString()
        }

        override fun exists(location: String) =
            DocumentFile.fromSingleUri(context, Uri.parse(location))?.exists() == true

        override fun delete(location: String) =
            DocumentFile.fromSingleUri(context, Uri.parse(location))?.delete() == true

        private fun dir(folders: List<String>, create: Boolean): DocumentFile? {
            dirCache[folders]?.let { return it }
            var current = tree ?: return null
            for ((depth, segment) in folders.withIndex()) {
                val key = folders.take(depth + 1)
                current = dirCache[key]
                    ?: (current.findFile(segment)?.takeIf { it.isDirectory }
                        ?: if (create) current.createDirectory(segment) ?: return null else return null)
                        .also { dirCache[key] = it }
            }
            return current
        }
    }

    companion object {
        /** The default: the app's folder in shared media storage, or private storage if that is unmounted. */
        fun phoneFolder(context: Context): PhoneFolder {
            @Suppress("DEPRECATION")
            val media = context.externalMediaDirs.firstOrNull { it != null }
            return PhoneFolder(File(media ?: context.filesDir, "LumiMusic"))
        }
    }
}

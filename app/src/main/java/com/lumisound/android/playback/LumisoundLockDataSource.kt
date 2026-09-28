package com.lumisound.android.playback

import android.net.Uri
import androidx.annotation.OptIn
import androidx.media3.common.util.UnstableApi
import androidx.media3.datasource.DataSource
import androidx.media3.datasource.DataSpec
import androidx.media3.datasource.TransferListener
import java.util.concurrent.ConcurrentHashMap

/**
 * Unmasks a Lumisound-locked (`.lms`) cloud track as it streams.
 *
 * A locked file is the iOS app's own container: an 8-byte `LMSLOCK1` magic header
 * followed by the real audio XOR-masked with a fixed 24-byte key. Nothing --
 * including ExoPlayer -- can decode those bytes until the transform is reversed,
 * and the bridge serves them masked on purpose, so the client has to do it.
 *
 * Unlike iOS and tvOS, which download the whole file and unlock it to a temp file
 * before handing it to AVFoundation, this reverses the mask in flight: XOR is
 * position-independent as long as the key's phase is kept (`payloadOffset % 24`),
 * so a range request starting anywhere in the file can be unmasked without having
 * read a byte before it. That keeps seeking and partial playback working on a
 * locked track exactly as they do on a plain one, with no full-file download and
 * no plaintext copy ever written to storage.
 *
 * Legacy `.lms` files -- produced before the real lock existed, whose bytes are
 * still a plain renamed container -- carry no magic header and are passed through
 * untouched, the same both other clients and the bridge itself do.
 */
@OptIn(UnstableApi::class)
class LumisoundLockDataSource(
    private val upstream: DataSource,
    /**
     * Opens a short-lived second connection used only to read a file's first 8
     * bytes. Needed because whether a locked track carries the magic header can
     * only be learned from the file, and a seek can ask for a mid-file range
     * before anything has read its start.
     */
    private val probeFactory: DataSource.Factory,
) : DataSource {

    private var maskActive = false
    private var logicalPosition = 0L
    private var openedUri: Uri? = null

    override fun addTransferListener(transferListener: TransferListener) =
        upstream.addTransferListener(transferListener)

    override fun open(dataSpec: DataSpec): Long {
        openedUri = dataSpec.uri
        logicalPosition = dataSpec.position

        if (dataSpec.uri.getQueryParameter(LOCKED_MARKER) != "1") {
            maskActive = false
            return upstream.open(dataSpec)
        }

        val headerBytes = headerOffsetFor(dataSpec)
        maskActive = headerBytes == MAGIC.size
        // The header is not part of the logical stream, so every request is shifted
        // past it. The length upstream reports back is already the payload length
        // remaining from here, which is exactly what the player should see.
        val shifted = if (headerBytes == 0) dataSpec else {
            dataSpec.buildUpon().setPosition(dataSpec.position + headerBytes).build()
        }
        return upstream.open(shifted)
    }

    override fun read(buffer: ByteArray, offset: Int, length: Int): Int {
        val read = upstream.read(buffer, offset, length)
        if (read <= 0 || !maskActive) {
            if (read > 0) logicalPosition += read
            return read
        }
        var phase = (logicalPosition % KEY.size).toInt()
        for (i in 0 until read) {
            buffer[offset + i] = (buffer[offset + i].toInt() xor KEY[phase].toInt()).toByte()
            phase++
            if (phase == KEY.size) phase = 0
        }
        logicalPosition += read
        return read
    }

    override fun getUri(): Uri? = upstream.uri ?: openedUri

    override fun getResponseHeaders(): Map<String, List<String>> = upstream.responseHeaders

    override fun close() {
        maskActive = false
        upstream.close()
    }

    /**
     * 8 for a file carrying the lock header, 0 for a legacy plain-renamed one.
     * Cached per URI: this costs an extra tiny request the first time a locked
     * track is opened, and nothing on any subsequent open or seek.
     */
    private fun headerOffsetFor(dataSpec: DataSpec): Int {
        val key = dataSpec.uri.toString()
        headerOffsets[key]?.let { return it }

        val probe = probeFactory.createDataSource()
        val offset = try {
            probe.open(
                DataSpec.Builder()
                    .setUri(dataSpec.uri)
                    .setPosition(0)
                    .setLength(MAGIC.size.toLong())
                    .build()
            )
            val head = ByteArray(MAGIC.size)
            var filled = 0
            while (filled < head.size) {
                val n = probe.read(head, filled, head.size - filled)
                if (n <= 0) break
                filled += n
            }
            if (filled == MAGIC.size && head.contentEquals(MAGIC)) MAGIC.size else 0
        } catch (e: Exception) {
            // Assume the modern format on a failed probe: every file this app or
            // Lumisound has uploaded since the real lock shipped carries the header,
            // and the alternative (guessing "legacy") would feed masked bytes to the
            // decoder as if they were audio.
            MAGIC.size
        } finally {
            try {
                probe.close()
            } catch (e: Exception) {
                // Probe teardown must not fail an otherwise fine open.
            }
        }
        headerOffsets[key] = offset
        return offset
    }

    /** Wraps any upstream factory so locked tracks unmask transparently. */
    class Factory(
        private val upstreamFactory: DataSource.Factory,
    ) : DataSource.Factory {
        override fun createDataSource(): DataSource =
            LumisoundLockDataSource(upstreamFactory.createDataSource(), upstreamFactory)
    }

    companion object {
        /** Query marker this app appends to a locked track's stream URL. */
        const val LOCKED_MARKER = "lms"

        private val MAGIC = "LMSLOCK1".toByteArray(Charsets.US_ASCII)

        /**
         * Byte-identical to `LumisoundLockFormat.key` (iOS), `TVLockFormat` (tvOS)
         * and `locked_media.LOCK_KEY` (bridge). Not a secret -- it ships in every
         * client -- and not meant to be: this is format lock-in, not DRM.
         */
        private val KEY = byteArrayOf(
            0x4C, 0x75, 0x6D, 0x69, 0x53, 0x6F, 0x75, 0x6E,
            0x64, 0x45, 0x78, 0x63, 0x6C, 0x75, 0x73, 0x69,
            0x76, 0x65, 0x4C, 0x6F, 0x63, 0x6B, 0x21, 0x21,
        )

        private val headerOffsets = ConcurrentHashMap<String, Int>()
    }
}

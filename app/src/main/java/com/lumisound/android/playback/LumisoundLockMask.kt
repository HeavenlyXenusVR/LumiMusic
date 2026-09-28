package com.lumisound.android.playback

/**
 * The byte-level half of the Lumisound lock, with no Android or player types
 * involved so it can be tested directly.
 *
 * A locked file is an 8-byte `LMSLOCK1` magic header followed by the real audio
 * XOR-masked with a fixed 24-byte key. XOR is its own inverse and is
 * position-independent as long as the key's phase is preserved, which is what
 * makes unmasking a mid-file range possible without having read anything before
 * it -- the property the streaming data source depends on.
 */
object LumisoundLockMask {

    /** Magic header, byte-identical to iOS, tvOS and the bridge. */
    val MAGIC: ByteArray = "LMSLOCK1".toByteArray(Charsets.US_ASCII)

    /**
     * App-embedded XOR key, byte-identical to `LumisoundLockFormat.key` (iOS),
     * `TVLockFormat` (tvOS) and `locked_media.LOCK_KEY` (bridge). Not a secret --
     * it ships in every client and is trivially recoverable from any of them.
     * That is fine for what this is: format lock-in, not DRM.
     */
    val KEY: ByteArray = byteArrayOf(
        0x4C, 0x75, 0x6D, 0x69, 0x53, 0x6F, 0x75, 0x6E,
        0x64, 0x45, 0x78, 0x63, 0x6C, 0x75, 0x73, 0x69,
        0x76, 0x65, 0x4C, 0x6F, 0x63, 0x6B, 0x21, 0x21,
    )

    /** True if [head] starts with the lock header. */
    fun hasMagic(head: ByteArray, length: Int = head.size): Boolean {
        if (length < MAGIC.size) return false
        for (i in MAGIC.indices) if (head[i] != MAGIC[i]) return false
        return true
    }

    /**
     * Unmasks [length] bytes in place, where [logicalPosition] is the offset of
     * `buffer[offset]` within the *payload* (i.e. the file position minus the
     * header). Returns nothing: the transform is its own inverse, so the same
     * call masks as well.
     */
    fun unmaskInPlace(buffer: ByteArray, offset: Int, length: Int, logicalPosition: Long) {
        var phase = (logicalPosition % KEY.size).toInt()
        for (i in 0 until length) {
            buffer[offset + i] = (buffer[offset + i].toInt() xor KEY[phase].toInt()).toByte()
            phase++
            if (phase == KEY.size) phase = 0
        }
    }
}

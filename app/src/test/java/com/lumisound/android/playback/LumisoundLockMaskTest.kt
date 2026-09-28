package com.lumisound.android.playback

import org.junit.Assert.assertArrayEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test
import kotlin.random.Random

/**
 * The lock transform has to agree with three other implementations
 * (`LumisoundLockFormat` on iOS, `TVLockFormat` on tvOS, `locked_media` on the
 * bridge) or a cloud track plays as noise. It also has to survive being applied to
 * an arbitrary mid-file range, because unlike the other clients this one unmasks
 * while streaming rather than after downloading the whole file.
 */
class LumisoundLockMaskTest {

    /** Exactly what the other implementations write to disk. */
    private fun lock(plain: ByteArray): ByteArray {
        val masked = plain.copyOf()
        LumisoundLockMask.unmaskInPlace(masked, 0, masked.size, 0L)
        return LumisoundLockMask.MAGIC + masked
    }

    /**
     * What `LumisoundLockDataSource` does for one read: the upstream range is
     * shifted past the header, and the payload offset drives the key phase.
     */
    private fun readThroughDataSource(locked: ByteArray, logicalPosition: Int, length: Int, headerBytes: Int): ByteArray {
        val from = logicalPosition + headerBytes
        val slice = locked.copyOfRange(from, minOf(from + length, locked.size))
        if (headerBytes == LumisoundLockMask.MAGIC.size) {
            LumisoundLockMask.unmaskInPlace(slice, 0, slice.size, logicalPosition.toLong())
        }
        return slice
    }

    @Test
    fun `sequential read of mixed chunk sizes reproduces the original`() {
        val plain = Random(7).nextBytes(200_000)
        val locked = lock(plain)
        val out = ByteArray(plain.size)
        var pos = 0
        val sizes = intArrayOf(1, 7, 24, 25, 4096, 65_536)
        var i = 0
        while (pos < plain.size) {
            val want = minOf(sizes[i++ % sizes.size], plain.size - pos)
            val chunk = readThroughDataSource(locked, pos, want, LumisoundLockMask.MAGIC.size)
            chunk.copyInto(out, pos)
            pos += want
        }
        assertArrayEquals(plain, out)
    }

    @Test
    fun `every seek offset unmasks correctly without reading what came before`() {
        val plain = Random(11).nextBytes(50_000)
        val locked = lock(plain)
        val random = Random(12)
        repeat(2_000) {
            val offset = random.nextInt(plain.size)
            val length = random.nextInt(1, 9_000).coerceAtMost(plain.size - offset)
            assertArrayEquals(
                "mismatch at offset $offset length $length",
                plain.copyOfRange(offset, offset + length),
                readThroughDataSource(locked, offset, length, LumisoundLockMask.MAGIC.size),
            )
        }
    }

    @Test
    fun `key phase boundaries are handled`() {
        val plain = Random(13).nextBytes(1_000)
        val locked = lock(plain)
        // The key repeats every 24 bytes, so these are the offsets a phase bug hides at.
        intArrayOf(0, 1, 23, 24, 25, 47, 48, 49, 96).forEach { offset ->
            assertArrayEquals(
                "mismatch at offset $offset",
                plain.copyOfRange(offset, offset + 40),
                readThroughDataSource(locked, offset, 40, LumisoundLockMask.MAGIC.size),
            )
        }
    }

    @Test
    fun `a legacy headerless file passes through untouched`() {
        // Files converted before the real lock existed are plain renamed containers.
        val plain = Random(17).nextBytes(4_000)
        assertArrayEquals(
            plain.copyOfRange(1_234, 1_734),
            readThroughDataSource(plain, 1_234, 500, headerBytes = 0),
        )
    }

    @Test
    fun `magic detection needs the whole header`() {
        val locked = lock(Random(19).nextBytes(64))
        assertTrue(LumisoundLockMask.hasMagic(locked))
        // A short read must not be taken as a match -- guessing "locked" on a partial
        // header would feed masked bytes to the decoder as if they were audio.
        assertFalse(LumisoundLockMask.hasMagic(locked, length = 4))
        assertFalse(LumisoundLockMask.hasMagic(ByteArray(8)))
    }

    @Test
    fun `masking twice is the identity`() {
        val plain = Random(23).nextBytes(999)
        val roundTrip = plain.copyOf()
        LumisoundLockMask.unmaskInPlace(roundTrip, 0, roundTrip.size, 0L)
        LumisoundLockMask.unmaskInPlace(roundTrip, 0, roundTrip.size, 0L)
        assertArrayEquals(plain, roundTrip)
    }
}

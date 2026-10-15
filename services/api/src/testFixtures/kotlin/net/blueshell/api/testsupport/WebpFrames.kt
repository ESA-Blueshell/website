package net.blueshell.api.testsupport

import java.nio.ByteBuffer
import java.nio.ByteOrder

/**
 * What a WebP's own frame table says, read from the container rather than decoded: the tests that
 * check an animation kept its timing have no decoder to lean on, and need none.
 */
object WebpFrames {
    /** How long each frame of [webp] is shown, in order, or nothing where it is a still. */
    fun durationsOf(webp: ByteArray): List<Int>? {
        val buffer = ByteBuffer.wrap(webp).order(ByteOrder.LITTLE_ENDIAN)
        val durations = mutableListOf<Int>()
        var at = RIFF_HEADER
        while (at + CHUNK_HEADER <= webp.size) {
            val id = String(webp, at, 4, Charsets.US_ASCII)
            val size = buffer.getInt(at + 4)
            if (id == "ANMF") durations += uint24(webp, at + CHUNK_HEADER + DURATION_OFFSET)
            at += CHUNK_HEADER + size + (size and 1)
        }
        return durations.takeIf { it.isNotEmpty() }
    }

    private fun uint24(
        bytes: ByteArray,
        at: Int,
    ) = (bytes[at].toInt() and 0xFF) or ((bytes[at + 1].toInt() and 0xFF) shl 8) or ((bytes[at + 2].toInt() and 0xFF) shl 16)

    private const val RIFF_HEADER = 12
    private const val CHUNK_HEADER = 8

    // An ANMF payload opens with the frame's offset, width and height, three bytes each.
    private const val DURATION_OFFSET = 12
}

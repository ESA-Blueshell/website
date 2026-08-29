package net.blueshell.api.file.domain

import java.nio.charset.StandardCharsets
import java.nio.file.Files
import java.nio.file.Path

/**
 * Reads the canvas size from a WebP container without decoding the pixels.
 *
 * Only the head of the file is looked at. The size lives in the first chunk, so a 15 MB upload
 * is never held in memory to answer a question the first few dozen bytes settle, and a chunk
 * whose declared payload runs past what was read still answers from the header fields that
 * were: a truncated picture has a size, and whether its pixels survive is the encoder's to say.
 */
object WebpDimensions {
    fun of(path: Path): ImageDimensions.Size? =
        runCatching {
            Files.newInputStream(path).use { it.readNBytes(HEADER_BYTES_READ) }
        }.getOrNull()?.let(::of)

    fun of(bytes: ByteArray): ImageDimensions.Size? {
        if (bytes.size < RIFF_HEADER_BYTES) return null
        if (ascii(bytes, 0) != "RIFF" || ascii(bytes, 8) != "WEBP") return null

        var offset = RIFF_HEADER_BYTES
        while (offset + CHUNK_HEADER_BYTES <= bytes.size) {
            val chunk = ascii(bytes, offset)
            val size = uint32(bytes, offset + 4)
            val data = offset + CHUNK_HEADER_BYTES
            val end = data.toLong() + size.toLong()
            if (size < 0) return null
            when (chunk) {
                "VP8X" -> return extended(bytes, data, size)
                "VP8L" -> return lossless(bytes, data, size)
                "VP8 " -> return lossy(bytes, data, size)
            }
            if (end > bytes.size) return null
            offset = (end + size.mod(2)).toInt()
        }
        return null
    }

    private fun extended(bytes: ByteArray, offset: Int, size: Int): ImageDimensions.Size? {
        if (size < 10 || offset + 10 > bytes.size) return null
        return ImageDimensions.Size(
            width = uint24(bytes, offset + 4) + 1,
            height = uint24(bytes, offset + 7) + 1,
        )
    }

    private fun lossless(bytes: ByteArray, offset: Int, size: Int): ImageDimensions.Size? {
        if (size < 5 || offset + 5 > bytes.size) return null
        if (byte(bytes, offset) != VP8L_SIGNATURE) return null
        val b1 = byte(bytes, offset + 1)
        val b2 = byte(bytes, offset + 2)
        val b3 = byte(bytes, offset + 3)
        val b4 = byte(bytes, offset + 4)
        return ImageDimensions.Size(
            width = 1 + (((b2 and 0x3f) shl 8) or b1),
            height = 1 + (((b4 and 0x0f) shl 10) or (b3 shl 2) or ((b2 and 0xc0) shr 6)),
        )
    }

    private fun lossy(bytes: ByteArray, offset: Int, size: Int): ImageDimensions.Size? {
        if (size < 10 || offset + 10 > bytes.size) return null
        if (byte(bytes, offset + 3) != 0x9d || byte(bytes, offset + 4) != 0x01 || byte(bytes, offset + 5) != 0x2a) {
            return null
        }
        return ImageDimensions.Size(
            width = uint16(bytes, offset + 6) and 0x3fff,
            height = uint16(bytes, offset + 8) and 0x3fff,
        )
    }

    private fun ascii(bytes: ByteArray, offset: Int): String =
        String(bytes, offset, 4, StandardCharsets.US_ASCII)

    private fun byte(bytes: ByteArray, offset: Int): Int = bytes[offset].toInt() and 0xff

    private fun uint16(bytes: ByteArray, offset: Int): Int =
        byte(bytes, offset) or (byte(bytes, offset + 1) shl 8)

    private fun uint24(bytes: ByteArray, offset: Int): Int =
        byte(bytes, offset) or (byte(bytes, offset + 1) shl 8) or (byte(bytes, offset + 2) shl 16)

    private fun uint32(bytes: ByteArray, offset: Int): Int {
        val value = byte(bytes, offset).toLong() or
            (byte(bytes, offset + 1).toLong() shl 8) or
            (byte(bytes, offset + 2).toLong() shl 16) or
            (byte(bytes, offset + 3).toLong() shl 24)
        return if (value > Int.MAX_VALUE) -1 else value.toInt()
    }

    /** Enough for the container header and the first chunk's own header, with room to spare. */
    private const val HEADER_BYTES_READ = 4096
    private const val RIFF_HEADER_BYTES = 12
    private const val CHUNK_HEADER_BYTES = 8
    private const val VP8L_SIGNATURE = 0x2f
}

package net.blueshell.api.file.domain

import org.assertj.core.api.Assertions.assertThat
import org.junit.jupiter.api.Nested
import org.junit.jupiter.api.Test
import java.io.ByteArrayInputStream
import java.nio.ByteBuffer
import java.nio.ByteOrder

class ImageDimensionsTest {
    /** The head of an extended WebP container: its chunk header and the canvas it declares. */
    private fun webpHeader(
        width: Int,
        height: Int,
    ): ByteArray =
        ByteBuffer
            .allocate(30)
            .order(ByteOrder.LITTLE_ENDIAN)
            .put("RIFF".toByteArray())
            .putInt(22)
            .put("WEBPVP8X".toByteArray())
            .putInt(10)
            .putInt(0)
            .put(uint24(width - 1))
            .put(uint24(height - 1))
            .array()

    private fun uint24(value: Int) = byteArrayOf(value.toByte(), (value shr 8).toByte(), (value shr 16).toByte())

    private fun sizeOf(bytes: ByteArray) = ImageDimensions.of(ByteArrayInputStream(bytes))

    /** A tall picture, so a transposed width and height would not pass as the right answer. */
    @Test
    fun `reads the size of a webp from its header`() {
        assertThat(sizeOf(webpHeader(4, 19))).isEqualTo(ImageDimensions.Size(4, 19))
    }

    /** The api decodes no uploaded picture, so a format other than WebP has no size here. */
    @Test
    fun `answers nothing for a png, whose header it does not read`() {
        val png = byteArrayOf(0x89.toByte(), 'P'.code.toByte(), 'N'.code.toByte(), 'G'.code.toByte(), 13, 10, 26, 10) + ByteArray(24)
        assertThat(sizeOf(png)).isNull()
    }

    /**
     * What a kind's ceiling does to a size.
     *
     * The rule is the one thing here that is arithmetic rather than I/O, and it is what decides
     * the width a stored picture actually has, so it is proven without touching a file.
     */
    @Nested
    inner class FittedWithin {
        /** Nothing is upscaled: a picture narrower than its ceiling keeps its own width. */
        @Test
        fun `leaves a size already within the ceiling alone`() {
            assertThat(ImageDimensions.Size(400, 240).fittedWithin(2560))
                .isEqualTo(ImageDimensions.Size(400, 240))
        }

        @Test
        fun `leaves a size sitting exactly on the ceiling alone`() {
            assertThat(ImageDimensions.Size(2560, 1440).fittedWithin(2560))
                .isEqualTo(ImageDimensions.Size(2560, 1440))
        }

        @Test
        fun `caps a wide picture by its width and scales the height with it`() {
            assertThat(ImageDimensions.Size(3000, 1200).fittedWithin(2560))
                .isEqualTo(ImageDimensions.Size(2560, 1024))
        }

        /** The ceiling governs the longest edge, so a portrait is capped by its height. */
        @Test
        fun `caps a tall picture by its height`() {
            assertThat(ImageDimensions.Size(1200, 3000).fittedWithin(2560))
                .isEqualTo(ImageDimensions.Size(1024, 2560))
        }

        /**
         * A panorama scales its short edge below half a pixel. Rounding it to nothing would
         * hand the encoder a zero-width target, which it refuses.
         */
        @Test
        fun `never scales an edge away to nothing`() {
            assertThat(ImageDimensions.Size(8000, 3).fittedWithin(512))
                .isEqualTo(ImageDimensions.Size(512, 1))
        }
    }

    @Test
    fun `answers nothing for bytes that are not a picture`() {
        assertThat(sizeOf("this is not a picture".toByteArray())).isNull()
    }

    @Test
    fun `answers nothing for an empty stream`() {
        assertThat(sizeOf(ByteArray(0))).isNull()
    }

    /**
     * A header that announces a picture and then stops. This is the case that decides whether
     * an unreadable upload is stored without its size or refused outright: it is stored.
     */
    @Test
    fun `answers nothing for a truncated picture`() {
        assertThat(sizeOf(webpHeader(12, 12).copyOfRange(0, 16))).isNull()
    }
}

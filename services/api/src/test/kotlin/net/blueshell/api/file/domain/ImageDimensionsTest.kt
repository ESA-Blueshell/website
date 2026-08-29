package net.blueshell.api.file.domain

import org.assertj.core.api.Assertions.assertThat
import org.junit.jupiter.api.Nested
import org.junit.jupiter.api.Test
import java.awt.image.BufferedImage
import java.io.ByteArrayInputStream
import java.io.ByteArrayOutputStream
import javax.imageio.ImageIO

class ImageDimensionsTest {

    private fun encoded(format: String, width: Int, height: Int): ByteArray =
        ByteArrayOutputStream().also { out ->
            ImageIO.write(BufferedImage(width, height, BufferedImage.TYPE_INT_RGB), format, out)
        }.toByteArray()

    private fun sizeOf(bytes: ByteArray) = ImageDimensions.of(ByteArrayInputStream(bytes))

    @Test
    fun `reads the size of a png`() {
        assertThat(sizeOf(encoded("png", 7, 3)))
            .isEqualTo(ImageDimensions.Size(7, 3))
    }

    @Test
    fun `reads the size of a jpeg`() {
        assertThat(sizeOf(encoded("jpg", 40, 25)))
            .isEqualTo(ImageDimensions.Size(40, 25))
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

    /** A tall picture, so a transposed width and height would not pass as the right answer. */
    @Test
    fun `does not transpose width and height`() {
        assertThat(sizeOf(encoded("png", 4, 19)))
            .isEqualTo(ImageDimensions.Size(4, 19))
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
     * an unreadable upload is stored without its size or refused outright — it is stored.
     */
    @Test
    fun `answers nothing for a truncated picture`() {
        val truncated = encoded("png", 12, 12).copyOfRange(0, 16)
        assertThat(sizeOf(truncated)).isNull()
    }
}

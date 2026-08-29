package net.blueshell.api.file.domain

import org.assertj.core.api.Assertions.assertThat
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

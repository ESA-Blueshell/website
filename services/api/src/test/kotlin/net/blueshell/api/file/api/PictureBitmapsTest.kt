package net.blueshell.api.file.api

import net.blueshell.api.file.domain.ScratchFile
import net.blueshell.api.file.domain.ScratchSpace
import net.blueshell.api.file.domain.WebpConversionException
import net.blueshell.api.file.domain.WebpEncoder
import net.blueshell.api.shared.enums.FileType
import net.blueshell.api.testsupport.Entities
import org.assertj.core.api.Assertions.assertThat
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.io.TempDir
import org.mockito.kotlin.any
import org.mockito.kotlin.doAnswer
import org.mockito.kotlin.doThrow
import org.mockito.kotlin.mock
import org.mockito.kotlin.whenever
import java.awt.image.BufferedImage
import java.io.ByteArrayOutputStream
import java.nio.file.Files
import java.nio.file.Path
import javax.imageio.ImageIO

class PictureBitmapsTest {
    @TempDir
    lateinit var directory: Path

    private val files: FileService = mock()
    private val webp: WebpEncoder = mock()
    private val pictures by lazy { PictureBitmaps(files, ScratchSpace(directory.toString()), webp) }

    private fun png(width: Int): ByteArray =
        ByteArrayOutputStream().also { ImageIO.write(BufferedImage(width, 10, BufferedImage.TYPE_INT_RGB), "png", it) }.toByteArray()

    @Test
    fun `reads a WebP through the converter and any other picture directly, and leaves no scratch behind`() {
        val photo = Entities.file(1, type = FileType.RECEIPT)
        val scan = Entities.file(2, type = FileType.RECEIPT, name = "scan.png", mediaType = "image/png")
        whenever(files.openContent(photo)).thenReturn("RIFF".byteInputStream())
        whenever(files.openContent(scan)).thenReturn(png(30).inputStream())
        doAnswer { Files.write((it.arguments[1] as ScratchFile).path, png(20)) }.whenever(webp).decode(any(), any())

        assertThat(pictures.bitmapOf(photo)!!.width).isEqualTo(20)
        assertThat(pictures.bitmapOf(scan)!!.width).isEqualTo(30)
        assertThat(Files.list(directory).count()).isZero()
    }

    @Test
    fun `answers nothing for a file it cannot read as a picture`() {
        val text = Entities.file(3, type = FileType.RECEIPT, name = "note.txt", mediaType = "text/plain")
        val bad = Entities.file(4, type = FileType.RECEIPT)
        whenever(files.openContent(text)).thenReturn("not a picture".byteInputStream())
        whenever(files.openContent(bad)).thenReturn("RIFF".byteInputStream())
        doThrow(WebpConversionException()).whenever(webp).decode(any(), any())

        assertThat(pictures.bitmapOf(text)).isNull()
        assertThat(pictures.bitmapOf(bad)).isNull()
        assertThat(Files.list(directory).count()).isZero()
    }
}

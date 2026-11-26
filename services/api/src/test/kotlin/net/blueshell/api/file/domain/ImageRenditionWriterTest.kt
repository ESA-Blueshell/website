package net.blueshell.api.file.domain

import net.blueshell.api.file.api.BlobStore
import net.blueshell.api.file.persistence.File
import net.blueshell.api.file.persistence.FileRepository
import net.blueshell.api.shared.enums.FileType
import org.assertj.core.api.Assertions.assertThat
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.io.TempDir
import org.mockito.kotlin.any
import org.mockito.kotlin.argumentCaptor
import org.mockito.kotlin.eq
import org.mockito.kotlin.mock
import org.mockito.kotlin.never
import org.mockito.kotlin.times
import org.mockito.kotlin.verify
import org.mockito.kotlin.whenever
import java.io.IOException
import java.nio.file.Path
import java.util.Optional

/**
 * What the ladder writes and records, given a converter.
 *
 * The ladder itself is proven end to end against the imaging service in `ImageRenditionsIT`, and
 * what the service does with frames that will not co-operate in its own suite.
 */
class ImageRenditionWriterTest {
    @TempDir
    private lateinit var directory: Path

    private val files: FileRepository = mock()
    private val converter: ImageConverter = mock()
    private val renditions: ImageConverter.Renditions = mock()
    private val blobs: BlobStore = mock()
    private val scratch: ScratchSpace by lazy { ScratchSpace(directory.toString()) }
    private val writer: ImageRenditionWriter by lazy { ImageRenditionWriter(files, converter, blobs, scratch) }

    private val source =
        File(
            name = "banner.gif",
            path = "event-banners/abc.webp",
            uploader = mock(),
            mediaType = "image/webp",
            size = 1,
            width = 1000,
            height = 400,
            type = FileType.EVENT_BANNER,
        )

    private fun stored(size: ImageDimensions.Size = ImageDimensions.Size(1000, 400)) {
        whenever(blobs.exists(source.path)).thenReturn(true)
        whenever(blobs.open(source.path)).thenReturn(byteArrayOf(1, 2, 3).inputStream())
        whenever(blobs.sizeOf(any())).thenReturn(null)
        whenever(blobs.put(any(), any())).thenReturn(1)
        whenever(files.findByPath(any())).thenReturn(Optional.empty())
        whenever(files.save(any<File>())).then { it.arguments[0] as File }
        whenever(converter.renditionsOf(any(), eq("image/webp"), eq(FileType.EVENT_BANNER), any(), eq(source.path))).thenReturn(renditions)
        whenever(renditions.size).thenReturn(size)
    }

    @Test
    fun `every width up to the picture is written and recorded at its shape`() {
        stored()

        val written = writer.derive(source)

        assertThat(written.map { it.renditionWidth to it.height })
            .containsExactly(160 to 64, 320 to 128, 480 to 192, 640 to 256, 960 to 384)
        verify(renditions, times(5)).write(any(), any())
        verify(renditions).close()
    }

    /** The converter's size is believed over the record: an animation's canvas is what its widths are cut from. */
    @Test
    fun `the widths follow the size the converter reads`() {
        stored(ImageDimensions.Size(500, 200))

        assertThat(writer.derive(source).mapNotNull { it.renditionWidth }).containsExactly(160, 320, 480)
    }

    @Test
    fun `a width the converter refuses is left out and the rest are written`() {
        stored()
        val target = argumentCaptor<ImageDimensions.Size>()
        whenever(renditions.write(target.capture(), any()))
            .then { if (target.lastValue.width == 640) throw WebpConversionException() else Unit }

        assertThat(writer.derive(source).mapNotNull { it.renditionWidth }).containsExactly(160, 320, 480, 960)
    }

    @Test
    fun `a width whose working copy cannot be written is left out`() {
        stored()
        val target = argumentCaptor<ImageDimensions.Size>()
        whenever(renditions.write(target.capture(), any()))
            .thenAnswer { if (target.lastValue.width == 480) throw IOException("disk full") else Unit }

        assertThat(writer.derive(source).mapNotNull { it.renditionWidth }).containsExactly(160, 320, 640, 960)
    }

    @Test
    fun `stored bytes with no record get their record and are not converted again`() {
        stored()
        whenever(blobs.sizeOf(any())).thenReturn(10)

        assertThat(writer.derive(source)).hasSize(5)
        verify(files, times(5)).save(any<File>())
        verify(renditions, never()).write(any(), any())
    }

    @Test
    fun `a record with no bytes has its bytes written again and keeps its record`() {
        stored()
        val record =
            File(
                name = source.name,
                path = "event-banners/abc-160.webp",
                uploader = source.uploader,
                mediaType = "image/webp",
                size = 1,
                type = source.type,
            )
        whenever(files.findByPath("event-banners/abc-160.webp")).thenReturn(Optional.of(record))

        val written = writer.derive(source)

        assertThat(written.first()).isSameAs(record)
        assertThat(record.width to record.height).isEqualTo(160 to 64)
        verify(renditions, times(5)).write(any(), any())
        verify(files, times(4)).save(any<File>())
    }

    @Test
    fun `says why a picture gets no widths`() {
        assertThat(writer.widthsOf(source.copyAs(mediaType = "image/svg+xml")).none).isEqualTo("A vector picture needs no widths.")
        assertThat(writer.widthsOf(source.copyAs(width = 100)).none).isEqualTo("The picture is narrower than every width it is served at.")
        assertThat(writer.widthsOf(source.copyAs(width = null)).none).isEqualTo("The picture's size is not recorded and cannot be read.")
        assertThat(writer.widthsOf(source).none).isEqualTo("The picture's bytes are not in storage.")
    }

    private fun File.copyAs(
        mediaType: String = this.mediaType,
        width: Int? = this.width,
    ) = File(name = name, path = path, uploader = uploader, mediaType = mediaType, size = size, width = width, height = height, type = type)
}

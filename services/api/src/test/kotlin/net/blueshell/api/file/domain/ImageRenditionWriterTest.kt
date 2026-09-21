package net.blueshell.api.file.domain

import net.blueshell.api.file.api.BlobStore
import net.blueshell.api.file.persistence.File
import net.blueshell.api.file.persistence.FileRepository
import net.blueshell.api.shared.enums.FileType
import org.assertj.core.api.Assertions.assertThat
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.io.TempDir
import org.mockito.kotlin.any
import org.mockito.kotlin.anyOrNull
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
 * What the ladder does when the frames will not co-operate.
 *
 * The ladder itself is proven end to end against the real converters in `ImageRenditionsIT`.
 * These are the answers that need a converter which fails in a particular way, which a working
 * `cwebp` cannot be asked for: a banner is still drawn at every width, without moving, rather
 * than left to every visitor at full size.
 */
class ImageRenditionWriterTest {
    @TempDir
    private lateinit var directory: Path

    private val files: FileRepository = mock()
    private val webpEncoder: WebpEncoder = mock()
    private val blobs: BlobStore = mock()
    private val animated: AnimatedImages = mock()
    private val scratch: ScratchSpace by lazy { ScratchSpace(directory.toString()) }
    private val writer: ImageRenditionWriter by lazy {
        ImageRenditionWriter(files, webpEncoder, blobs, scratch, animated)
    }

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

    private fun stored() {
        whenever(blobs.exists(source.path)).thenReturn(true)
        whenever(blobs.open(source.path)).thenReturn(byteArrayOf(1, 2, 3).inputStream())
        whenever(blobs.sizeOf(any())).thenReturn(null)
        whenever(blobs.put(any(), any())).thenReturn(1)
        whenever(files.findByPath(any())).thenReturn(Optional.empty())
        whenever(files.save(any<File>())).then { it.arguments[0] as File }
        whenever(animated.mayAnimate("image/webp")).thenReturn(true)
    }

    /**
     * A master whose frames the converter will not read is stored still, not skipped.
     *
     * Skipping would leave the page fetching the master at every viewport, which is the weight
     * the ladder exists to take off a phone.
     */
    @Test
    fun `a master whose frames cannot be read is stored still at every width`() {
        stored()
        whenever(animated.framesOf(any(), eq("image/webp"))).thenThrow(WebpConversionException())

        val written = writer.derive(source)

        assertThat(written.mapNotNull { it.renditionWidth }).containsExactly(320, 640, 960)
        verify(webpEncoder, times(3)).encode(any(), any(), anyOrNull(), any(), anyOrNull())
        verify(animated, never()).write(any(), any(), anyOrNull(), any(), anyOrNull())
    }

    /** The same answer when the working copy could not be read at all, rather than refused. */
    @Test
    fun `a master whose frames cannot be opened is stored still at every width`() {
        stored()
        // Answered rather than thrown: Kotlin has no checked exceptions, so the mock refuses to
        // be told to throw one against a signature that does not declare it.
        whenever(animated.framesOf(any(), eq("image/webp"))).thenAnswer { throw IOException("no such volume") }

        assertThat(writer.derive(source).mapNotNull { it.renditionWidth }).containsExactly(320, 640, 960)
    }

    /**
     * One width that will not reassemble falls back to its first frame, and the rest still move.
     *
     * Per width rather than per picture: a converter that choked once has no say over the
     * widths it did manage.
     */
    @Test
    fun `a width that will not reassemble is written as its first frame`() {
        stored()
        val frames = frames()
        whenever(animated.framesOf(any(), eq("image/webp"))).thenReturn(frames)
        val target = argumentCaptor<ImageDimensions.Size>()
        whenever(animated.write(eq(frames), any(), anyOrNull(), any(), target.capture()))
            .then { if (target.lastValue.width == 640) throw WebpConversionException() else Unit }

        val written = writer.derive(source)

        assertThat(written.mapNotNull { it.renditionWidth }).containsExactly(320, 640, 960)
        // The one width that fell back, and only that one.
        verify(webpEncoder, times(1)).encode(any(), any(), anyOrNull(), any(), anyOrNull())
    }

    /** Frames of the source, sized so that the same three widths are below it. */
    private fun frames(): FrameSequence =
        FrameSequence(
            listOf(FrameSequence.Frame(scratch.cut(".png"), 100), FrameSequence.Frame(scratch.cut(".png"), 100)),
            ImageDimensions.Size(1000, 400),
        )
}

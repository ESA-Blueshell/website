package net.blueshell.api.file.domain

import net.blueshell.api.shared.enums.FileType
import org.assertj.core.api.Assertions.assertThat
import org.assertj.core.api.Assertions.assertThatThrownBy
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
import java.awt.image.BufferedImage
import java.io.IOException
import java.nio.file.Path
import javax.imageio.ImageIO

/**
 * What the converter does when the frames will not co-operate.
 *
 * Masters and renditions are proven end to end against the real converters in `ImageRenditionsIT`
 * and `AnimatedImagesIT`. These are the answers that need a converter which fails in a particular
 * way, which a working `cwebp` cannot be asked for: a banner is still drawn at every width, without
 * moving, rather than left to every visitor at full size.
 */
class InProcessImageConverterTest {
    @TempDir
    private lateinit var directory: Path

    private val webpEncoder: WebpEncoder = mock()
    private val animated: AnimatedImages = mock()
    private val scratch: ScratchSpace by lazy { ScratchSpace(directory.toString()) }
    private val converter: InProcessImageConverter by lazy { InProcessImageConverter(scratch, webpEncoder, animated) }

    private val recorded = ImageDimensions.Size(1000, 400)

    private fun renditions() =
        converter.renditionsOf(scratch.cut(".webp"), "image/webp", FileType.EVENT_BANNER, recorded, "event-banners/abc.webp")

    private fun writeEvery(renditions: ImageConverter.Renditions) =
        listOf(160, 320, 640).forEach { width -> scratch.cut(".webp").use { renditions.write(ImageDimensions.Size(width, 64), it) } }

    @Test
    fun `a master whose frames cannot be read is drawn still at every width`() {
        whenever(animated.mayAnimate("image/webp")).thenReturn(true)
        whenever(animated.framesOf(any(), eq("image/webp"))).thenThrow(WebpConversionException())

        renditions().use { renditions ->
            assertThat(renditions.size).isEqualTo(recorded)
            writeEvery(renditions)
        }

        verify(webpEncoder, times(3)).encode(any(), any(), anyOrNull(), any(), anyOrNull())
        verify(animated, never()).write(any(), any(), anyOrNull(), any(), anyOrNull())
    }

    @Test
    fun `a master whose frames cannot be opened is drawn still at every width`() {
        whenever(animated.mayAnimate("image/webp")).thenReturn(true)
        // Answered rather than thrown: Kotlin has no checked exceptions, so the mock refuses to
        // be told to throw one against a signature that does not declare it.
        whenever(animated.framesOf(any(), eq("image/webp"))).thenAnswer { throw IOException("no such volume") }

        renditions().use(::writeEvery)

        verify(webpEncoder, times(3)).encode(any(), any(), anyOrNull(), any(), anyOrNull())
    }

    /** Per width rather than per picture: a converter that choked once has no say over the widths it did manage. */
    @Test
    fun `a width that will not reassemble is drawn as its first frame`() {
        whenever(animated.mayAnimate("image/webp")).thenReturn(true)
        val frames = frames()
        whenever(animated.framesOf(any(), eq("image/webp"))).thenReturn(frames)
        val target = argumentCaptor<ImageDimensions.Size>()
        whenever(animated.write(eq(frames), any(), anyOrNull(), any(), target.capture()))
            .then { if (target.lastValue.width == 320) throw WebpConversionException() else Unit }

        renditions().use { renditions ->
            assertThat(renditions.size).isEqualTo(frames.size)
            writeEvery(renditions)
        }

        verify(animated, times(3)).write(eq(frames), any(), anyOrNull(), any(), anyOrNull())
        verify(webpEncoder, times(1)).encode(eq(frames.frames.first().bytes), any(), anyOrNull(), any(), anyOrNull())
    }

    @Test
    fun `an animation is kept as one under the ceiling`() {
        whenever(animated.mayAnimate("image/gif")).thenReturn(true)
        val frames = frames(ImageDimensions.Size(4000, 1000))
        whenever(animated.framesOf(any(), eq("image/gif"))).thenReturn(frames)

        val master = converter.master(scratch.cut(".gif"), "image/gif", FileType.EVENT_BANNER)

        master.bytes.close()
        assertThat(master.size).isEqualTo(ImageDimensions.Size(2560, 640))
        verify(animated).write(eq(frames), any(), anyOrNull(), any(), eq(master.size))
        verify(webpEncoder, never()).encode(any(), any(), anyOrNull(), any(), anyOrNull())
    }

    @Test
    fun `a still is brought under the ceiling keeping its shape`() {
        val source = scratch.cut(".png")
        ImageIO.write(BufferedImage(3000, 100, BufferedImage.TYPE_INT_RGB), "png", source.path.toFile())

        val master = converter.master(source, "image/png", FileType.EVENT_BANNER)

        master.bytes.close()
        assertThat(master.size).isEqualTo(ImageDimensions.Size(2560, 85))
        verify(webpEncoder).encode(eq(source), any(), eq(82), eq(false), eq(master.size))
    }

    @Test
    fun `a still master is drawn still at every width`() {
        val renditions = converter.renditionsOf(scratch.cut(".webp"), "image/webp", FileType.EVENT_BANNER, recorded, "abc.webp")

        renditions.use(::writeEvery)

        assertThat(renditions.size).isEqualTo(recorded)
        verify(webpEncoder, times(3)).encode(any(), any(), anyOrNull(), any(), anyOrNull())
        verify(animated, never()).framesOf(any(), any())
    }

    @Test
    fun `a picture that cannot be decoded is refused as an invalid file`() {
        val source = scratch.cut(".png").also { it.path.toFile().writeBytes(byteArrayOf(1, 2, 3)) }

        assertThatThrownBy { converter.master(source, "image/png", FileType.EVENT_BANNER) }
            .isInstanceOf(InvalidFileException::class.java)
    }

    private fun frames(size: ImageDimensions.Size = recorded): FrameSequence =
        FrameSequence(listOf(FrameSequence.Frame(scratch.cut(".png"), 100), FrameSequence.Frame(scratch.cut(".png"), 100)), size)
}

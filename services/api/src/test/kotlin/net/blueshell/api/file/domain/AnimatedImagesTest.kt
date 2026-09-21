package net.blueshell.api.file.domain

import org.assertj.core.api.Assertions.assertThat
import org.assertj.core.api.Assertions.assertThatThrownBy
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.io.TempDir
import org.mockito.kotlin.any
import org.mockito.kotlin.eq
import org.mockito.kotlin.mock
import org.mockito.kotlin.whenever
import java.nio.file.Files
import java.nio.file.Path

/**
 * What the frame seam does when the converter will not co-operate.
 *
 * The round trip against the real binaries is `AnimatedImagesIT`. These are the answers that
 * depend on the converter failing or on it succeeding without producing anything, which a real
 * `cwebp` cannot be asked for.
 */
class AnimatedImagesTest {
    @TempDir
    private lateinit var directory: Path

    private val scratch: ScratchSpace by lazy { ScratchSpace(directory.toString()) }

    private val encoder: WebpEncoder = mock()

    private val animated: AnimatedImages by lazy { AnimatedImages(scratch, encoder) }

    @Test
    fun `only the two formats that hold frames may animate`() {
        assertThat(animated.mayAnimate("image/gif")).isTrue()
        assertThat(animated.mayAnimate("image/webp")).isTrue()
        assertThat(animated.mayAnimate("image/webp; charset=binary")).isTrue()
        assertThat(animated.mayAnimate("image/png")).isFalse()
        assertThat(animated.mayAnimate("application/pdf")).isFalse()
    }

    /** A format with one frame by definition is never taken apart, whatever it claims. */
    @Test
    fun `a format that cannot hold frames has none`() {
        file(".png").use { source ->
            assertThat(animated.framesOf(source, "image/png")).isNull()
        }
    }

    @Test
    fun `a still WebP has no frames`() {
        file(".webp").use { source ->
            whenever(encoder.animationOf(source)).thenReturn(null)

            assertThat(animated.framesOf(source, "image/webp")).isNull()
        }
    }

    /**
     * A converter that refuses halfway leaves nothing behind.
     *
     * The frames already cut are working copies on the same volume the uploads sit on, so a
     * failure that walked away from them would fill that volume one refused banner at a time.
     */
    @Test
    fun `a refusal halfway through takes the frames already cut with it`() {
        file(".webp").use { source ->
            whenever(encoder.animationOf(source)).thenReturn(WebpAnimation(listOf(100, 100, 100)))
            whenever(encoder.frameOf(eq(source), eq(3), any())).thenThrow(WebpConversionException())

            assertThatThrownBy { animated.framesOf(source, "image/webp") }
                .isInstanceOf(WebpConversionException::class.java)

            assertThat(leftBehind()).isZero()
        }
    }

    /**
     * A converter that answers without writing anything.
     *
     * Not a refusal, so nothing throws, and a frame that cannot be measured is a frame nothing
     * can be resized from. Answered as a still rather than as an animation of unreadable parts.
     */
    @Test
    fun `frames that cannot be measured are not an animation`() {
        file(".webp").use { source ->
            whenever(encoder.animationOf(source)).thenReturn(WebpAnimation(listOf(100, 100)))

            assertThat(animated.framesOf(source, "image/webp")).isNull()
            assertThat(leftBehind()).isZero()
        }
    }

    /** Only a GIF has to be handed over as something else; everything else `cwebp` reads. */
    @Test
    fun `a still the converter already reads is not rewritten`() {
        file(".png").use { source ->
            assertThat(animated.readableStillOf(source, "image/png")).isNull()
        }
    }

    /**
     * How many working copies are on the volume besides the one the test is holding.
     *
     * The frames are cut beside the uploads, so a failure that walked away from them would fill
     * that volume one refused banner at a time.
     */
    private fun leftBehind(): Int = Files.list(directory).use { paths -> paths.count() }.toInt() - 1

    private fun file(suffix: String): ScratchFile = scratch.cut(suffix)
}

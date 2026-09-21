package net.blueshell.api.file.domain

import net.blueshell.api.testsupport.AnimatedGifs
import org.assertj.core.api.Assertions.assertThat
import org.assertj.core.api.Assertions.assertThatThrownBy
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.io.TempDir
import java.nio.file.Files
import java.nio.file.Path

/**
 * An animation taken apart, resized and put back together, against the real converters.
 *
 * Proven as a round trip rather than as a command line: what matters is that an animation
 * written here can be read back here, because that is exactly what a rendition does to a
 * master. A frame optimisation the converter is free to apply would break that, and only bytes
 * going out and coming back would show it.
 */
class AnimatedImagesIT {
    @TempDir
    private lateinit var directory: Path

    private val scratch: ScratchSpace by lazy { ScratchSpace(directory.toString()) }

    private val encoder: WebpEncoder by lazy { WebpEncoder("cwebp", "dwebp", "webpmux").also(WebpEncoder::verifyAvailable) }

    private val animated: AnimatedImages by lazy { AnimatedImages(scratch, encoder) }

    @Test
    fun `a gif keeps its frames and their delays through a conversion`() {
        gif(AnimatedGifs.patched(64, 48)).use { source ->
            animated.framesOf(source, GIF)!!.use { frames ->
                scratch.cut(".webp").use { master ->
                    animated.write(frames, master, quality = 82, lossless = false)

                    assertThat(master.open().use(WebpDimensions::isAnimated)).isTrue()
                    assertThat(master.open().use(WebpDimensions::of)).isEqualTo(ImageDimensions.Size(64, 48))
                    assertThat(encoder.animationOf(master)?.durationsMillis).containsExactly(120, 80, 200, 40)
                }
            }
        }
    }

    /**
     * The round trip a rendition is: a stored master, taken apart again and written narrower.
     *
     * Every frame comes back, at the width asked for, with its delay, which is what a visitor
     * on a phone gets instead of the master.
     */
    @Test
    fun `an animated master is derived again at a narrower width`() {
        gif(AnimatedGifs.patched(64, 48)).use { source ->
            val master = scratch.cut(".webp")
            master.use {
                animated.framesOf(source, GIF)!!.use { frames ->
                    animated.write(frames, master, quality = 82, lossless = false)
                }

                animated.framesOf(master, WEBP)!!.use { frames ->
                    assertThat(frames.frames).hasSize(4)
                    assertThat(frames.size).isEqualTo(ImageDimensions.Size(64, 48))
                    assertThat(frames.frames.map { it.durationMillis }).containsExactly(120, 80, 200, 40)

                    scratch.cut(".webp").use { narrower ->
                        animated.write(frames, narrower, 82, false, ImageDimensions.Size(32, 24))

                        assertThat(narrower.open().use(WebpDimensions::isAnimated)).isTrue()
                        assertThat(narrower.open().use(WebpDimensions::of)).isEqualTo(ImageDimensions.Size(32, 24))
                        assertThat(encoder.animationOf(narrower)?.durationsMillis).containsExactly(120, 80, 200, 40)
                    }
                }
            }
        }
    }

    /** A picture of one frame is a still, and the ordinary converter is what handles a still. */
    @Test
    fun `a single frame is not taken apart`() {
        gif(AnimatedGifs.single()).use { source ->
            assertThat(animated.framesOf(source, GIF)).isNull()
        }
    }

    /** The still converter refuses a GIF outright, so one has to be handed over as something else. */
    @Test
    fun `a gif is offered to the still converter as something it reads`() {
        gif(AnimatedGifs.single(40, 30)).use { source ->
            animated.readableStillOf(source, GIF)!!.use { still ->
                scratch.cut(".webp").use { encoded ->
                    encoder.encode(still, encoded, quality = 82, lossless = false)

                    assertThat(encoded.open().use(WebpDimensions::of)).isEqualTo(ImageDimensions.Size(40, 30))
                }
            }
        }
    }

    @Test
    fun `a picture that is not a gif is handed to the still converter as it is`() {
        gif(AnimatedGifs.single()).use { source ->
            assertThat(animated.readableStillOf(source, WEBP)).isNull()
        }
    }

    /**
     * Every refusal the three binaries can answer with, taken one at a time.
     *
     * The converter's own words name a temporary path, so what a caller gets is the refusal
     * rather than the sentence: these assert that each of them is one, and not a stack trace
     * from somewhere inside the subprocess handling.
     */
    @Test
    fun `the converter refuses bytes that are not a picture, once per thing it is asked`() {
        gif(byteArrayOf(1, 2, 3, 4)).use { rubbish ->
            scratch.cut(".webp").use { out ->
                assertThatThrownBy { encoder.animationOf(rubbish) }
                    .isInstanceOf(WebpConversionException::class.java)
                assertThatThrownBy { encoder.decode(rubbish, out) }
                    .isInstanceOf(WebpConversionException::class.java)
                assertThatThrownBy { encoder.frameOf(rubbish, 1, out) }
                    .isInstanceOf(WebpConversionException::class.java)
                assertThatThrownBy { encoder.mux(listOf(WebpEncoder.AnimationFrame(rubbish, 100)), out) }
                    .isInstanceOf(WebpConversionException::class.java)
            }
        }
    }

    /** An animation of no frames is a caller's mistake, not something to ask the converter. */
    @Test
    fun `an animation needs at least one frame`() {
        scratch.cut(".webp").use { out ->
            assertThatThrownBy { encoder.mux(emptyList(), out) }
                .isInstanceOf(IllegalArgumentException::class.java)
        }
    }

    private fun gif(bytes: ByteArray): ScratchFile = scratch.cut(".gif").also { file -> Files.write(file.path, bytes) }

    private companion object {
        const val GIF = "image/gif"
        const val WEBP = "image/webp"
    }
}

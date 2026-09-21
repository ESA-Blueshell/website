package net.blueshell.api.file.domain

import net.blueshell.api.testsupport.AnimatedGifs
import org.assertj.core.api.Assertions.assertThat
import org.assertj.core.api.Assertions.assertThatThrownBy
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.io.TempDir
import java.awt.Color
import java.nio.file.FileAlreadyExistsException
import java.nio.file.Files
import java.nio.file.Path
import javax.imageio.ImageIO

/**
 * A GIF taken apart into the frames a viewer sees, rather than the rectangles it stores.
 *
 * The patches are the point. A GIF frame is a rectangle drawn onto whatever the frames before
 * it left behind, so a reader that hands each rectangle straight to the converter produces an
 * animation of torn fragments, which decodes, converts and serves without complaint.
 */
class GifFramesTest {
    @TempDir
    private lateinit var directory: Path

    private val scratch: ScratchSpace by lazy { ScratchSpace(directory.toString()) }

    @Test
    fun `every frame is the whole canvas, with the frames before it still on it`() {
        gif(AnimatedGifs.patched(64, 48)).use { source ->
            val frames = GifFrames.of(source.path, scratch)

            assertThat(frames).isNotNull
            frames!!.use {
                assertThat(frames.size).isEqualTo(ImageDimensions.Size(64, 48))
                assertThat(frames.frames).hasSize(4)
                frames.frames.forEach { frame ->
                    val image = ImageIO.read(frame.bytes.path.toFile())
                    assertThat(image.width).isEqualTo(64)
                    assertThat(image.height).isEqualTo(48)
                    // Outside every patch, so it is the first frame showing through.
                    assertThat(Color(image.getRGB(60, 44))).isEqualTo(Color.RED)
                }

                // The green patch is drawn on the second frame and disposes of nothing, so the
                // third is drawn over it. The third restores what was there before it, so the
                // fourth is drawn over green again rather than over blue.
                assertThat(patchColourOf(frames.frames[0])).isEqualTo(Color.RED)
                assertThat(patchColourOf(frames.frames[1])).isEqualTo(Color.GREEN)
                assertThat(patchColourOf(frames.frames[2])).isEqualTo(Color.BLUE)
                assertThat(patchColourOf(frames.frames[3])).isEqualTo(Color.WHITE)
            }
        }
    }

    @Test
    fun `the frames keep the delays the file asked for`() {
        gif(AnimatedGifs.patched()).use { source ->
            GifFrames.of(source.path, scratch)!!.use { frames ->
                assertThat(frames.frames.map { it.durationMillis }).containsExactly(120, 80, 200, 40)
            }
        }
    }

    /** One frame is a still: it gets the ordinary ladder rather than an animation of length one. */
    @Test
    fun `a gif of one frame is not an animation`() {
        gif(AnimatedGifs.single()).use { source ->
            assertThat(GifFrames.of(source.path, scratch)).isNull()
        }
    }

    @Test
    fun `something that is not a gif has no frames`() {
        gif(byteArrayOf(1, 2, 3, 4)).use { source ->
            assertThat(GifFrames.of(source.path, scratch)).isNull()
        }
    }

    /** The still converter will not read a GIF, so even a single frame has to come out as PNG. */
    @Test
    fun `the first frame comes out as something the converter reads`() {
        gif(AnimatedGifs.single(40, 30)).use { source ->
            GifFrames.firstFrameOf(source.path, scratch)!!.use { still ->
                val image = ImageIO.read(still.path.toFile())
                assertThat(image.width).isEqualTo(40)
                assertThat(image.height).isEqualTo(30)
            }
        }
    }

    /**
     * A volume that will not take a working copy takes the frames already written with it.
     *
     * The frames are cut beside the uploads, so a failure partway that walked away from the
     * ones already on disk would fill that volume one refused banner at a time.
     */
    @Test
    fun `frames already written are cleaned up when the next one cannot be cut`() {
        val source = Files.createTempFile(directory, "source-", ".gif")
        Files.write(source, AnimatedGifs.patched())
        // A file where the working copies would go, so cutting one cannot succeed.
        val blocked = Files.createTempFile(directory, "blocked-", ".tmp")

        assertThatThrownBy { GifFrames.of(source, ScratchSpace(blocked.toString())) }
            .isInstanceOf(FileAlreadyExistsException::class.java)

        assertThat(Files.list(directory).use { paths -> paths.count() }).isEqualTo(2)
    }

    private fun patchColourOf(frame: FrameSequence.Frame): Color =
        Color(ImageIO.read(frame.bytes.path.toFile()).getRGB(PATCH_SAMPLE, PATCH_SAMPLE))

    private fun gif(bytes: ByteArray): ScratchFile =
        scratch.cut(".gif").also { file -> Files.write(file.path, bytes) }

    private companion object {
        /** Inside the 10px patch drawn at (10, 10). */
        const val PATCH_SAMPLE = 12
    }
}

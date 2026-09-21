package net.blueshell.api.file.domain

import org.assertj.core.api.Assertions.assertThat
import org.junit.jupiter.api.Test

/**
 * The frame durations of a stored animation, read back out of the converter's own report.
 *
 * Worth a test of its own because it is a fixed-width table parsed by column: every rendition
 * of a banner runs at whatever this says, so a column read one place to the left would make
 * every copy of every animated banner run at the wrong speed and nothing else would notice.
 */
class WebpAnimationTest {
    @Test
    fun `an animation answers one duration per frame, in order`() {
        val animation = WebpAnimation.of(ANIMATION)

        assertThat(animation).isNotNull
        assertThat(animation!!.durationsMillis).containsExactly(120, 80, 200)
        assertThat(animation.frameCount).isEqualTo(3)
    }

    @Test
    fun `a still is not an animation`() {
        assertThat(WebpAnimation.of(STILL)).isNull()
    }

    /** One frame is a still, whatever container it arrived in. */
    @Test
    fun `an animation of one frame is not an animation`() {
        assertThat(WebpAnimation.of(SINGLE_FRAME)).isNull()
    }

    @Test
    fun `output from something else is not an animation`() {
        assertThat(WebpAnimation.of("")).isNull()
        assertThat(WebpAnimation.of("Error! Cannot read input file")).isNull()
    }

    private companion object {
        val ANIMATION =
            """
            Canvas size: 32 x 24
            Features present: animation
            Background color : 0xFFFFFFFF  Loop Count : 0
            Number of frames: 3
            No.: width height alpha x_offset y_offset duration   dispose blend image_size  compression
              1:    32    24    no        0        0      120       none    no         82       lossy
              2:    32    24    no        0        0       80       none    no         84       lossy
              3:    32    24    no        0        0      200       none    no         82       lossy
            """.trimIndent()

        val STILL =
            """
            Canvas size: 32 x 24
            No features present.
            """.trimIndent()

        val SINGLE_FRAME =
            """
            Canvas size: 32 x 24
            Features present: animation
            Background color : 0xFFFFFFFF  Loop Count : 0
            Number of frames: 1
            No.: width height alpha x_offset y_offset duration   dispose blend image_size  compression
              1:    32    24    no        0        0      120       none    no         82       lossy
            """.trimIndent()
    }
}

package net.blueshell.api.pinger.domain

import org.assertj.core.api.Assertions.assertThat
import org.junit.jupiter.api.Test
import java.time.Instant

class CanvasMotionTest {
    private val epoch = Instant.parse("2026-10-09T20:00:00Z")

    @Test
    fun `reflect leaves a point inside the range alone`() {
        assertThat(CanvasMotion.reflect(0.0, 100.0)).isEqualTo(0.0)
        assertThat(CanvasMotion.reflect(42.5, 100.0)).isEqualTo(42.5)
        assertThat(CanvasMotion.reflect(100.0, 100.0)).isEqualTo(100.0)
    }

    @Test
    fun `reflect bounces off the far edge and back off the near one`() {
        assertThat(CanvasMotion.reflect(130.0, 100.0)).isEqualTo(70.0)
        assertThat(CanvasMotion.reflect(200.0, 100.0)).isEqualTo(0.0)
        assertThat(CanvasMotion.reflect(230.0, 100.0)).isEqualTo(30.0)
        assertThat(CanvasMotion.reflect(1030.0, 100.0)).isEqualTo(30.0)
    }

    @Test
    fun `reflect bounces a negative position off the near edge`() {
        assertThat(CanvasMotion.reflect(-30.0, 100.0)).isEqualTo(30.0)
        assertThat(CanvasMotion.reflect(-130.0, 100.0)).isEqualTo(70.0)
        assertThat(CanvasMotion.reflect(-200.0, 100.0)).isEqualTo(0.0)
    }

    @Test
    fun `reflect pins a box as wide as the canvas to zero`() {
        assertThat(CanvasMotion.reflect(55.0, 0.0)).isEqualTo(0.0)
        assertThat(CanvasMotion.reflect(55.0, -10.0)).isEqualTo(0.0)
    }

    @Test
    fun `a static box stays at its origin however long it has been`() {
        val at = epoch.plusSeconds(3600)

        val position = CanvasMotion.positionAt(100, 200, 800, 600, MotionMode.STATIC, 500.0, 500.0, epoch, at)

        assertThat(position).isEqualTo(100 to 200)
    }

    @Test
    fun `a bouncing box travels at its speed and reflects within the canvas less its size`() {
        // L_x = 3040, L_y = 1560. After 2.5 s: x = 100 + 2500 = 2600, y = 200 - 1000 = -800 -> 800.
        val position = CanvasMotion.positionAt(100, 200, 800, 600, MotionMode.BOUNCE, 1000.0, -400.0, epoch, epoch.plusMillis(2500))

        assertThat(position).isEqualTo(2600 to 800)
    }

    @Test
    fun `a bouncing box past the far edge comes back`() {
        // x = 3000 + 100 = 3100 past L_x = 3040 -> 2980.
        val position = CanvasMotion.positionAt(3000, 0, 800, 600, MotionMode.BOUNCE, 100.0, 0.0, epoch, epoch.plusSeconds(1))

        assertThat(position).isEqualTo(2980 to 0)
    }

    @Test
    fun `a box filling the canvas cannot move`() {
        val position = CanvasMotion.positionAt(0, 0, 3840, 2160, MotionMode.BOUNCE, 500.0, 500.0, epoch, epoch.plusSeconds(7))

        assertThat(position).isEqualTo(0 to 0)
    }

    @Test
    fun `the mode reads and writes its lowercase wire name`() {
        assertThat(MotionMode.BOUNCE.wire).isEqualTo("bounce")
        assertThat(MotionMode.ofWire("static")).isEqualTo(MotionMode.STATIC)
    }
}

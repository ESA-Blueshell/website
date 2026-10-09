package net.blueshell.api.pinger.domain

import java.time.Duration
import java.time.Instant
import kotlin.math.roundToInt

/**
 * Where a moving placement's box is at a given instant, on the 3840x2160 canvas.
 *
 * With L_x = 3840 − width, L_y = 2160 − height and s the seconds since the motion epoch,
 * x(s) = reflect(originX + vx·s, L_x) and y(s) = reflect(originY + vy·s, L_y), where
 * reflect(p, L) = 0 when L ≤ 0, else m = ((p mod 2L) + 2L) mod 2L and the result is m when m ≤ L,
 * 2L − m otherwise. A static placement stays at its origin. The pinger and the website implement the
 * same rule against the paint job's serverTime; change one, change the others.
 */
object CanvasMotion {
    const val CANVAS_WIDTH = 3840
    const val CANVAS_HEIGHT = 2160

    private const val NANOS_PER_SECOND = 1_000_000_000.0

    fun reflect(
        p: Double,
        limit: Double,
    ): Double {
        if (limit <= 0) return 0.0
        val period = 2 * limit
        val m = ((p % period) + period) % period
        return if (m <= limit) m else period - m
    }

    /** The box's top-left corner at [at], rounded to whole pixels. */
    @Suppress("LongParameterList")
    fun positionAt(
        originX: Int,
        originY: Int,
        width: Int,
        height: Int,
        mode: MotionMode,
        vx: Double,
        vy: Double,
        epoch: Instant,
        at: Instant,
    ): Pair<Int, Int> {
        if (mode == MotionMode.STATIC) return originX to originY
        val seconds = Duration.between(epoch, at).toNanos() / NANOS_PER_SECOND
        val x = reflect(originX + vx * seconds, (CANVAS_WIDTH - width).toDouble())
        val y = reflect(originY + vy * seconds, (CANVAS_HEIGHT - height).toDouble())
        return x.roundToInt() to y.roundToInt()
    }
}

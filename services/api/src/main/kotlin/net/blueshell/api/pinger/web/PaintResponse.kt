package net.blueshell.api.pinger.web

import io.swagger.v3.oas.annotations.media.Schema
import net.blueshell.api.pinger.api.PaintView
import net.blueshell.api.pinger.api.PlacementMotion
import net.blueshell.api.pinger.api.PlacementView
import net.blueshell.api.pinger.domain.MotionMode
import java.time.Instant

/** The paint job as the pinger, the helper exe and the admin page read it back. */
@Schema(description = "The prefix, rate and the images the pinger paints with")
data class PaintResponse(
    val prefix: String?,
    val ratePps: Int,
    @field:Schema(description = "Whether the always-on SiteCie painter contributes; ratePps is its rate")
    val siteCieEnabled: Boolean,
    @field:Schema(description = "The images on the canvas, each with its box, in draw order")
    val placements: List<PlacementResponse>,
    @field:Schema(description = "The api's clock when this was built; place moving boxes against it, not the local clock")
    val serverTime: Instant,
) {
    companion object {
        fun from(view: PaintView) =
            PaintResponse(
                prefix = view.prefix,
                ratePps = view.ratePps,
                siteCieEnabled = view.siteCieEnabled,
                placements = view.placements.map { PlacementResponse.from(it) },
                serverTime = view.serverTime,
            )
    }
}

/**
 * One image on the canvas, with the box it lands in at [motionEpoch]. Where a moving box is later
 * follows net.blueshell.api.pinger.domain.CanvasMotion, which the [motion] schema spells out for
 * the clients that mirror it.
 */
@Schema(description = "One image on the canvas and the box it lands in at motionEpoch")
data class PlacementResponse(
    val id: Long,
    val imageUrl: String,
    val originX: Int,
    val originY: Int,
    val width: Int,
    val height: Int,
    @field:Schema(
        description =
            "How the box moves from its origin. x(s) = reflect(originX + vx*s, 3840 - width), " +
                "y(s) = reflect(originY + vy*s, 2160 - height), s seconds since motionEpoch; " +
                "reflect(p, L) = 0 if L <= 0, else m = ((p mod 2L) + 2L) mod 2L, m <= L ? m : 2L - m",
    )
    val motion: MotionResponse,
    @field:Schema(description = "When the box stood at its origin and the motion started")
    val motionEpoch: Instant,
) {
    companion object {
        fun from(view: PlacementView) =
            PlacementResponse(
                id = view.id,
                imageUrl = view.imageUrl,
                originX = view.originX,
                originY = view.originY,
                width = view.width,
                height = view.height,
                motion = MotionResponse.from(view.motion),
                motionEpoch = view.motionEpoch,
            )
    }
}

/** A placement's motion; a static one has zero velocity. */
@Schema(description = "A placement's motion: static, or bouncing off the canvas edges at (vx, vy) px/s")
data class MotionResponse(
    val mode: MotionMode,
    val vx: Double,
    val vy: Double,
) {
    companion object {
        fun from(motion: PlacementMotion) = MotionResponse(motion.mode, motion.vx, motion.vy)
    }
}

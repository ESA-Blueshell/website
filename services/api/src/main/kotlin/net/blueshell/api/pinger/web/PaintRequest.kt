package net.blueshell.api.pinger.web

import io.swagger.v3.oas.annotations.media.Schema
import jakarta.validation.constraints.DecimalMax
import jakarta.validation.constraints.DecimalMin
import jakarta.validation.constraints.Max
import jakarta.validation.constraints.Min
import jakarta.validation.constraints.NotBlank
import jakarta.validation.constraints.Pattern
import net.blueshell.api.pinger.domain.MotionMode

/**
 * An admin's edit to the canvas settings: the prefix, the rate and the SiteCie toggle. The prefix is
 * loose here and parsed strictly by the pinger; an empty one leaves the pinger idle. The images and
 * their boxes are edited through the placement endpoints.
 */
@Schema(description = "An admin's edit to the paint-job settings")
data class PaintSettingsRequest(
    @field:Schema(description = "The SNTPings /64 to paint towards, e.g. 2001:db8:b317:a000::/64; empty leaves the pinger idle")
    @field:Pattern(regexp = "^$|^[0-9a-fA-F:]+(/\\d{1,3})?$", message = "That is not an IPv6 prefix.")
    val prefix: String? = null,
    @field:Min(1)
    @field:Max(200_000)
    val ratePps: Int,
    @field:Schema(description = "Whether the always-on SiteCie painter contributes; ratePps above is its rate")
    val siteCieEnabled: Boolean = true,
)

/**
 * An admin adding an image to the canvas: where it is stored and the box it lands in. The box is
 * checked against the canvas in the service, since the limit on each side depends on its origin.
 */
@Schema(description = "An admin adding an image to the canvas in its own box")
data class PlacementRequest(
    @field:Schema(description = "Where the image is stored, as returned by the image upload")
    @field:NotBlank
    val imagePath: String,
    @field:Min(0) @field:Max(3839) val originX: Int,
    @field:Min(0) @field:Max(2159) val originY: Int,
    @field:Min(1) @field:Max(3840) val width: Int,
    @field:Min(1) @field:Max(2160) val height: Int,
)

/** An admin moving or resizing one placement's box. The image stays; only the box changes. */
@Schema(description = "An admin moving or resizing a placement's box")
data class PlacementBoxRequest(
    @field:Min(0) @field:Max(3839) val originX: Int,
    @field:Min(0) @field:Max(2159) val originY: Int,
    @field:Min(1) @field:Max(3840) val width: Int,
    @field:Min(1) @field:Max(2160) val height: Int,
)

/** An admin setting how one placement moves. A static placement drops its velocity. */
@Schema(description = "An admin setting a placement's motion")
data class PlacementMotionRequest(
    val mode: MotionMode,
    // Mirrors MAX_SPEED_PPS in PingerPaintService; change one, change the other.
    @field:Schema(description = "Horizontal speed in px/s, negative is leftwards")
    @field:DecimalMin("-2000") @field:DecimalMax("2000") val vx: Double = 0.0,
    @field:Schema(description = "Vertical speed in px/s, negative is upwards")
    @field:DecimalMin("-2000") @field:DecimalMax("2000") val vy: Double = 0.0,
)

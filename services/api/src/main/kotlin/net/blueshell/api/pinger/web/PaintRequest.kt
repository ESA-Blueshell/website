package net.blueshell.api.pinger.web

import io.swagger.v3.oas.annotations.media.Schema
import jakarta.validation.constraints.Max
import jakarta.validation.constraints.Min
import jakarta.validation.constraints.Pattern

/**
 * An admin's edit to the paint job. The box is checked against the canvas in the service, since the
 * limit on each side depends on its origin. The prefix is loose here and parsed strictly by the
 * pinger; an empty one leaves the pinger idle.
 */
@Schema(description = "An admin's edit to the paint job")
data class PaintRequest(
    @field:Schema(description = "The SNTPings /64 to paint towards, e.g. 2001:db8:b317:a000::/64; empty leaves the pinger idle")
    @field:Pattern(regexp = "^$|^[0-9a-fA-F:]+(/\\d{1,3})?$", message = "That is not an IPv6 prefix.")
    val prefix: String? = null,
    @field:Min(1)
    @field:Max(200_000)
    val ratePps: Int,
    @field:Min(0)
    @field:Max(3839)
    val originX: Int,
    @field:Min(0)
    @field:Max(2159)
    val originY: Int,
    @field:Min(1)
    @field:Max(3840)
    val width: Int,
    @field:Min(1)
    @field:Max(2160)
    val height: Int,
    @field:Schema(description = "Where the image is stored, as returned by the image upload; nothing takes it away")
    val imagePath: String? = null,
)

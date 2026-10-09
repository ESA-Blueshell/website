package net.blueshell.api.pinger.web

import io.swagger.v3.oas.annotations.media.Schema
import net.blueshell.api.pinger.api.PingerShare

@Schema(
    description =
        "The slice of the paint this device takes: it keeps pixel i (0-based, placements concatenated in " +
            "descriptor order) iff frac((i + 1) * 0.6180339887498949) lies in [from, to)",
)
data class PingerShareResponse(
    @field:Schema(description = "Inclusive start of the slice, in [0, 1)")
    val from: Double,
    @field:Schema(description = "Exclusive end of the slice, in (0, 1]; the last device's is exactly 1")
    val to: Double,
    @field:Schema(description = "How many devices share the paint, this one included")
    val devices: Int,
) {
    companion object {
        fun of(share: PingerShare) = PingerShareResponse(share.from, share.to, share.devices)
    }
}

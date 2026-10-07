package net.blueshell.api.pinger.web

import io.swagger.v3.oas.annotations.media.Schema
import net.blueshell.api.pinger.api.PaintView

/** The paint job as the pinger, the helper exe and the admin page read it back. */
@Schema(description = "The image, prefix, rate and placement the pinger paints with")
data class PaintResponse(
    val prefix: String?,
    val ratePps: Int,
    val originX: Int,
    val originY: Int,
    val width: Int,
    val height: Int,
    val imageUrl: String?,
    @field:Schema(description = "Whether the always-on SiteCie painter contributes; ratePps is its rate")
    val siteCieEnabled: Boolean,
) {
    companion object {
        fun from(view: PaintView) =
            PaintResponse(
                prefix = view.prefix,
                ratePps = view.ratePps,
                originX = view.originX,
                originY = view.originY,
                width = view.width,
                height = view.height,
                imageUrl = view.imageUrl,
                siteCieEnabled = view.siteCieEnabled,
            )
    }
}

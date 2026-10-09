package net.blueshell.api.pinger.web

import io.swagger.v3.oas.annotations.media.Schema
import net.blueshell.api.pinger.api.PaintView
import net.blueshell.api.pinger.api.PlacementView

/** The paint job as the pinger, the helper exe and the admin page read it back. */
@Schema(description = "The prefix, rate and the images the pinger paints with")
data class PaintResponse(
    val prefix: String?,
    val ratePps: Int,
    @field:Schema(description = "Whether the always-on SiteCie painter contributes; ratePps is its rate")
    val siteCieEnabled: Boolean,
    @field:Schema(description = "The images on the canvas, each with its box, in draw order")
    val placements: List<PlacementResponse>,
) {
    companion object {
        fun from(view: PaintView) =
            PaintResponse(
                prefix = view.prefix,
                ratePps = view.ratePps,
                siteCieEnabled = view.siteCieEnabled,
                placements = view.placements.map { PlacementResponse.from(it) },
            )
    }
}

/** One image on the canvas, with the box it lands in. */
@Schema(description = "One image on the canvas and the box it lands in")
data class PlacementResponse(
    val id: Long,
    val imageUrl: String,
    val originX: Int,
    val originY: Int,
    val width: Int,
    val height: Int,
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
            )
    }
}

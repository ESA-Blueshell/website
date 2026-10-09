package net.blueshell.api.pinger.web

import io.swagger.v3.oas.annotations.tags.Tag
import jakarta.annotation.security.PermitAll
import jakarta.validation.Valid
import net.blueshell.api.pinger.api.PingerPaintService
import net.blueshell.api.security.AdminOnly
import org.springframework.http.HttpStatus
import org.springframework.web.bind.annotation.DeleteMapping
import org.springframework.web.bind.annotation.GetMapping
import org.springframework.web.bind.annotation.PathVariable
import org.springframework.web.bind.annotation.PostMapping
import org.springframework.web.bind.annotation.PutMapping
import org.springframework.web.bind.annotation.RequestBody
import org.springframework.web.bind.annotation.RequestMapping
import org.springframework.web.bind.annotation.ResponseStatus
import org.springframework.web.bind.annotation.RestController

/**
 * The paint job the pinger reads and an admin steers.
 *
 * Reading is public: the pinger pod, the helper exe and any onlooker fetch the same descriptor
 * anonymously. Only an admin edits it, from the main site. The settings are one row; each image is
 * its own placement, added, moved and removed on its own. The images are uploaded through the
 * shared image endpoint and served publicly through the file module; this only holds their paths.
 */
@RestController
@RequestMapping("/pinger")
@Tag(name = "Pinger", description = "The images the association paints on the SNTPings canvas")
class PingerController(
    private val paint: PingerPaintService,
) {
    @PermitAll
    @GetMapping("/paint")
    fun paint(): PaintResponse = PaintResponse.from(paint.current())

    @AdminOnly
    @PutMapping("/paint/settings")
    fun setSettings(
        @Valid @RequestBody request: PaintSettingsRequest,
    ): PaintResponse =
        PaintResponse.from(
            paint.updateSettings(
                prefix = request.prefix,
                ratePps = request.ratePps,
                siteCieEnabled = request.siteCieEnabled,
            ),
        )

    @AdminOnly
    @PostMapping("/paint/placements")
    fun addPlacement(
        @Valid @RequestBody request: PlacementRequest,
    ): PlacementResponse =
        PlacementResponse.from(
            paint.addPlacement(
                imagePath = request.imagePath,
                originX = request.originX,
                originY = request.originY,
                width = request.width,
                height = request.height,
            ),
        )

    @AdminOnly
    @PutMapping("/paint/placements/{id}")
    fun movePlacement(
        @PathVariable id: Long,
        @Valid @RequestBody request: PlacementBoxRequest,
    ): PlacementResponse =
        PlacementResponse.from(
            paint.movePlacement(
                id = id,
                originX = request.originX,
                originY = request.originY,
                width = request.width,
                height = request.height,
            ),
        )

    @AdminOnly
    @DeleteMapping("/paint/placements/{id}")
    @ResponseStatus(HttpStatus.NO_CONTENT)
    fun removePlacement(
        @PathVariable id: Long,
    ) = paint.removePlacement(id)
}

package net.blueshell.api.pinger.web

import io.swagger.v3.oas.annotations.tags.Tag
import jakarta.annotation.security.PermitAll
import jakarta.validation.Valid
import net.blueshell.api.pinger.api.PingerPaintService
import net.blueshell.api.security.AdminOnly
import org.springframework.web.bind.annotation.GetMapping
import org.springframework.web.bind.annotation.PutMapping
import org.springframework.web.bind.annotation.RequestBody
import org.springframework.web.bind.annotation.RequestMapping
import org.springframework.web.bind.annotation.RestController

/**
 * The paint job the pinger reads and an admin steers.
 *
 * Reading is public: the pinger pod, the helper exe and any onlooker fetch the same descriptor
 * anonymously. Only an admin edits it, from the main site. The image itself is uploaded through the
 * shared image endpoint and served publicly through the file module; this only holds its path.
 */
@RestController
@RequestMapping("/pinger")
@Tag(name = "Pinger", description = "The image the association paints on the SNTPings canvas")
class PingerController(
    private val paint: PingerPaintService,
) {
    @PermitAll
    @GetMapping("/paint")
    fun paint(): PaintResponse = PaintResponse.from(paint.current())

    @AdminOnly
    @PutMapping("/paint")
    fun setPaint(
        @Valid @RequestBody request: PaintRequest,
    ): PaintResponse =
        PaintResponse.from(
            paint.update(
                prefix = request.prefix,
                ratePps = request.ratePps,
                originX = request.originX,
                originY = request.originY,
                width = request.width,
                height = request.height,
                imagePath = request.imagePath,
            ),
        )
}

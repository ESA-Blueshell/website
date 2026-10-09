package net.blueshell.api.pinger.api

import net.blueshell.api.file.api.PublicFileUrls
import net.blueshell.api.file.api.StoredPictures
import net.blueshell.api.pinger.persistence.PingerPaint
import net.blueshell.api.pinger.persistence.PingerPaintRepository
import net.blueshell.api.pinger.persistence.PingerPlacement
import net.blueshell.api.pinger.persistence.PingerPlacementRepository
import net.blueshell.api.shared.enums.FileType
import org.springframework.http.HttpStatus
import org.springframework.stereotype.Service
import org.springframework.transaction.annotation.Transactional
import org.springframework.web.server.ResponseStatusException

/** The 4K canvas the pinger paints onto, mirrored from the pinger's own bounds. */
private const val CANVAS_WIDTH = 3840
private const val CANVAS_HEIGHT = 2160

/** The sender's rate cap, mirrored from the pinger's MaxRatePPS. */
private const val MAX_RATE_PPS = 200_000

@Service
class PingerPaintService(
    private val repository: PingerPaintRepository,
    private val placements: PingerPlacementRepository,
    private val pictures: StoredPictures,
) {
    @Transactional(readOnly = true)
    fun current(): PaintView = viewOf(row(), placements.findAllByOrderByOrdinalAscIdAsc())

    /** Updates the settings the whole canvas shares: the prefix, the rate and the SiteCie toggle. */
    @Transactional
    fun updateSettings(
        prefix: String?,
        ratePps: Int,
        siteCieEnabled: Boolean,
    ): PaintView {
        if (ratePps !in 1..MAX_RATE_PPS) {
            throw ResponseStatusException(HttpStatus.BAD_REQUEST, "The rate must be from 1 to $MAX_RATE_PPS.")
        }
        val row = row()
        row.prefix = prefix?.trim()?.ifBlank { null }
        row.ratePps = ratePps
        row.siteCieEnabled = siteCieEnabled
        return viewOf(repository.save(row), placements.findAllByOrderByOrdinalAscIdAsc())
    }

    /** Adds an image to the canvas in its own box. The image is resolved here so a bad path is refused. */
    @Transactional
    fun addPlacement(
        imagePath: String,
        originX: Int,
        originY: Int,
        width: Int,
        height: Int,
    ): PlacementView {
        checkBox(originX, originY, width, height)
        val stored = resolveImage(imagePath)
        val next = (placements.findAll().maxOfOrNull { it.ordinal } ?: -1) + 1
        val placement =
            placements.save(
                PingerPlacement(imagePath = stored, originX = originX, originY = originY, width = width, height = height, ordinal = next),
            )
        return placementView(placement)
    }

    /** Moves or resizes one placement's box. The image stays; only its box changes. */
    @Transactional
    fun movePlacement(
        id: Long,
        originX: Int,
        originY: Int,
        width: Int,
        height: Int,
    ): PlacementView {
        checkBox(originX, originY, width, height)
        val placement = placements.findById(id).orElseThrow { notFound(id) }
        placement.originX = originX
        placement.originY = originY
        placement.width = width
        placement.height = height
        return placementView(placements.save(placement))
    }

    @Transactional
    fun removePlacement(id: Long) {
        if (!placements.existsById(id)) throw notFound(id)
        placements.deleteById(id)
    }

    // Widen before summing: the ints are bean-validated, but adding two request ints is an overflow
    // pattern.
    @Suppress("ComplexCondition")
    private fun checkBox(
        originX: Int,
        originY: Int,
        width: Int,
        height: Int,
    ) {
        if (originX < 0 ||
            originY < 0 ||
            width < 1 ||
            height < 1 ||
            originX.toLong() + width > CANVAS_WIDTH ||
            originY.toLong() + height > CANVAS_HEIGHT
        ) {
            throw ResponseStatusException(
                HttpStatus.BAD_REQUEST,
                "The box runs off the ${CANVAS_WIDTH}x$CANVAS_HEIGHT canvas.",
            )
        }
    }

    // StoredPictures throws when a path names nothing stored, which is the refusal a bad path earns.
    private fun resolveImage(imagePath: String): String =
        pictures.of(imagePath, FileType.PINGER_PAINT)?.path
            ?: throw ResponseStatusException(HttpStatus.BAD_REQUEST, "That image is not in storage.")

    private fun notFound(id: Long) = ResponseStatusException(HttpStatus.NOT_FOUND, "No placement with id $id.")

    private fun row(): PingerPaint = repository.findById(1L).orElseGet { PingerPaint(ratePps = 128) }

    private fun viewOf(
        row: PingerPaint,
        placed: List<PingerPlacement>,
    ): PaintView =
        PaintView(
            prefix = row.prefix,
            ratePps = row.ratePps,
            siteCieEnabled = row.siteCieEnabled,
            placements = placed.map { placementView(it) },
        )

    // Built from the stored path, not a fresh lookup: a public read must not 400 if the file is
    // later removed. This is exactly what PublicFileUrls.of(file) would return.
    private fun placementView(placement: PingerPlacement): PlacementView =
        PlacementView(
            id = placement.id!!,
            imageUrl = "${PublicFileUrls.PATH}/${placement.imagePath}",
            originX = placement.originX,
            originY = placement.originY,
            width = placement.width,
            height = placement.height,
        )
}

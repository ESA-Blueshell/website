package net.blueshell.api.pinger.api

import net.blueshell.api.file.api.PublicFileUrls
import net.blueshell.api.file.api.StoredPictures
import net.blueshell.api.pinger.domain.CanvasMotion
import net.blueshell.api.pinger.domain.CanvasMotion.CANVAS_HEIGHT
import net.blueshell.api.pinger.domain.CanvasMotion.CANVAS_WIDTH
import net.blueshell.api.pinger.domain.MotionMode
import net.blueshell.api.pinger.persistence.PingerPaint
import net.blueshell.api.pinger.persistence.PingerPaintRepository
import net.blueshell.api.pinger.persistence.PingerPlacement
import net.blueshell.api.pinger.persistence.PingerPlacementRepository
import net.blueshell.api.shared.enums.FileType
import org.springframework.http.HttpStatus
import org.springframework.stereotype.Service
import org.springframework.transaction.annotation.Transactional
import org.springframework.web.server.ResponseStatusException
import java.time.Clock
import java.time.Instant
import java.time.temporal.ChronoUnit
import kotlin.math.abs

/** The sender's rate cap, mirrored from the pinger's MaxRatePPS. */
private const val MAX_RATE_PPS = 200_000

/** The fastest a placement may travel on either axis, in px/s. Mirrored by PlacementMotionRequest. */
const val MAX_SPEED_PPS = 2000.0

@Service
class PingerPaintService(
    private val repository: PingerPaintRepository,
    private val placements: PingerPlacementRepository,
    private val pictures: StoredPictures,
    private val clock: Clock = Clock.systemUTC(),
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
                PingerPlacement(
                    imagePath = stored,
                    originX = originX,
                    originY = originY,
                    width = width,
                    height = height,
                    ordinal = next,
                    motionEpoch = now(),
                ),
            )
        return placementView(placement)
    }

    /** Moves or resizes one placement's box. The image stays, and any motion restarts from the new box. */
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
        placement.motionEpoch = now()
        return placementView(placements.save(placement))
    }

    /**
     * Sets how one placement moves. The box is re-based to where it is right now and the epoch to now,
     * so switching motion never makes it jump.
     */
    @Transactional
    fun setMotion(
        id: Long,
        mode: MotionMode,
        vx: Double,
        vy: Double,
    ): PlacementView {
        // Written so a NaN fails it too.
        if (!(abs(vx) <= MAX_SPEED_PPS && abs(vy) <= MAX_SPEED_PPS)) {
            throw ResponseStatusException(HttpStatus.BAD_REQUEST, "A speed must be at most ${MAX_SPEED_PPS.toInt()} px/s.")
        }
        val placement = placements.findById(id).orElseThrow { notFound(id) }
        val now = now()
        val (x, y) = positionAt(placement, now)
        placement.originX = x
        placement.originY = y
        placement.motionMode = mode
        placement.motionVx = if (mode == MotionMode.STATIC) 0.0 else vx
        placement.motionVy = if (mode == MotionMode.STATIC) 0.0 else vy
        placement.motionEpoch = now
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

    private fun positionAt(
        placement: PingerPlacement,
        at: Instant,
    ): Pair<Int, Int> =
        CanvasMotion.positionAt(
            originX = placement.originX,
            originY = placement.originY,
            width = placement.width,
            height = placement.height,
            mode = placement.motionMode,
            vx = placement.motionVx,
            vy = placement.motionVy,
            epoch = placement.motionEpoch,
            at = at,
        )

    // Milliseconds: the column keeps no finer, so the epoch a write returns is the one a read sees.
    private fun now(): Instant = clock.instant().truncatedTo(ChronoUnit.MILLIS)

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
            serverTime = clock.instant(),
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
            motion = PlacementMotion(placement.motionMode, placement.motionVx, placement.motionVy),
            motionEpoch = placement.motionEpoch,
        )
}

package net.blueshell.api.pinger.api

import net.blueshell.api.file.api.PublicFileUrls
import net.blueshell.api.file.api.StoredPictures
import net.blueshell.api.pinger.persistence.PingerPaint
import net.blueshell.api.pinger.persistence.PingerPaintRepository
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
    private val pictures: StoredPictures,
) {
    @Transactional(readOnly = true)
    fun current(): PaintView = viewOf(row())

    @Transactional
    fun update(
        prefix: String?,
        ratePps: Int,
        originX: Int,
        originY: Int,
        width: Int,
        height: Int,
        imagePath: String?,
    ): PaintView {
        // Long arithmetic: the inputs are bounded by bean validation, but adding two request ints
        // is an overflow pattern, so widen before the sum.
        if (originX.toLong() + width > CANVAS_WIDTH || originY.toLong() + height > CANVAS_HEIGHT) {
            throw ResponseStatusException(
                HttpStatus.BAD_REQUEST,
                "The box runs off the ${CANVAS_WIDTH}x$CANVAS_HEIGHT canvas.",
            )
        }
        if (ratePps !in 1..MAX_RATE_PPS) {
            throw ResponseStatusException(HttpStatus.BAD_REQUEST, "The rate must be from 1 to $MAX_RATE_PPS.")
        }
        // Resolve the image now so a bad path is refused here rather than leaving the pinger to fail
        // on an image it cannot fetch. StoredPictures throws when a path names nothing stored.
        val stored = pictures.of(imagePath, FileType.PINGER_PAINT)
        val row = row()
        row.prefix = prefix?.trim()?.ifBlank { null }
        row.ratePps = ratePps
        row.originX = originX
        row.originY = originY
        row.width = width
        row.height = height
        row.imagePath = stored?.path
        return viewOf(repository.save(row))
    }

    private fun row(): PingerPaint =
        repository.findById(PingerPaint.SINGLETON_ID).orElseGet {
            PingerPaint(ratePps = 128, originX = 1470, originY = 180, width = 900, height = 720)
        }

    private fun viewOf(row: PingerPaint): PaintView =
        PaintView(
            prefix = row.prefix,
            ratePps = row.ratePps,
            originX = row.originX,
            originY = row.originY,
            width = row.width,
            height = row.height,
            // Built from the stored path, not a fresh lookup: a public read must not 400 if the
            // file is later removed. This is exactly what PublicFileUrls.of(file) would return.
            imageUrl = row.imagePath?.let { "${PublicFileUrls.PATH}/$it" },
        )
}

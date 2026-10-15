package net.blueshell.api.file.domain

import java.io.InputStream
import kotlin.math.max
import kotlin.math.roundToInt

/**
 * How large a stored picture is, read from its WebP header and never by decoding it: the api
 * decodes no uploaded picture (architecture ADR-014). Every picture a page shows is stored as
 * WebP, so every one of them has a size.
 *
 * Anything else answers null. That is an answer, not a failure: the file is still stored and
 * served, just drawn without its space reserved, and never a reason to refuse an upload.
 */
object ImageDimensions {
    data class Size(
        val width: Int,
        val height: Int,
    ) {
        /** The edge a kind's ceiling governs. */
        val longestEdge: Int get() = max(width, height)

        /**
         * This size brought under [maxEdge], keeping its shape. One already within the ceiling is
         * returned as it is, since nothing is upscaled, and an edge that rounds below a pixel is
         * held at one so an extreme panorama still gives the encoder a target.
         */
        fun fittedWithin(maxEdge: Int): Size {
            val edge = longestEdge
            if (edge <= maxEdge) return this

            val ratio = maxEdge.toDouble() / edge.toDouble()
            return Size(
                width = max(1, (width * ratio).roundToInt()),
                height = max(1, (height * ratio).roundToInt()),
            )
        }
    }

    /**
     * Whether a file of this media type is worth opening for a size.
     *
     * Asked in SQL too, by `FileRepository.findImagesMissingDimensions`, which cannot call this:
     * change one, change the other. A publicly readable kind admits only image media types, so a
     * picture on a page always answers true.
     */
    fun mayHaveSize(mediaType: String): Boolean = mediaType.startsWith("image/")

    /** The size of some bytes, from their WebP header, or nothing where they are not WebP. */
    fun of(content: InputStream): Size? = WebpDimensions.of(content)
}

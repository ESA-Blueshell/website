package net.blueshell.api.file.domain

import org.slf4j.LoggerFactory
import java.io.IOException
import java.io.InputStream
import java.nio.file.Files
import java.nio.file.Path
import javax.imageio.ImageIO
import kotlin.math.max
import kotlin.math.roundToInt

/**
 * How large a stored picture is, read from its header rather than by decoding it: the size sits
 * in the first few bytes, and decoding would allocate a whole bitmap to throw it away.
 *
 * An unreadable or unregistered format answers null. That is an answer, not a failure — the
 * file is still stored and served, just drawn without its space reserved — and never a reason
 * to refuse an upload. WebP is read from its container header, having no ImageIO reader.
 */
object ImageDimensions {

    data class Size(val width: Int, val height: Int) {

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

    /** The size of a stored file, or nothing where it is missing or cannot be read. */
    fun of(path: Path): Size? =
        try {
            if (Files.exists(path)) {
                WebpDimensions.of(path) ?: Files.newInputStream(path).use(::of)
            } else {
                null
            }
        } catch (e: IOException) {
            log.warn("Could not read the size of a stored picture: {}", e.message)
            null
        }

    fun of(stream: InputStream): Size? = runCatching {
        ImageIO.createImageInputStream(stream)?.use { input ->
            val readers = ImageIO.getImageReaders(input)
            if (!readers.hasNext()) return@use null
            val reader = readers.next()
            try {
                reader.input = input
                Size(reader.getWidth(reader.minIndex), reader.getHeight(reader.minIndex))
            } finally {
                reader.dispose()
            }
        }
    }.getOrNull()

    private val log = LoggerFactory.getLogger(ImageDimensions::class.java)
}

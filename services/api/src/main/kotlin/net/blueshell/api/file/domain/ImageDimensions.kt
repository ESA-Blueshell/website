package net.blueshell.api.file.domain

import org.slf4j.LoggerFactory
import java.io.IOException
import java.io.InputStream
import java.nio.file.Files
import java.nio.file.Path
import javax.imageio.ImageIO

/**
 * How large a stored picture is, read from the picture itself.
 *
 * The header is read rather than the image decoded: the size sits in the first few bytes of
 * every format here, and decoding a 2560-pixel photograph to learn its width would allocate
 * the whole bitmap in order to throw it away.
 *
 * A format no reader is registered for answers null, and so does anything malformed. That is
 * an answer rather than a failure: the file is still stored and still served, it is simply
 * drawn without its space reserved. It is never a reason to refuse an upload.
 *
 * WebP is one of those formats — the platform registers no reader for it. Nothing stored today
 * is WebP, and the conversion work that makes everything WebP knows the size it produced, so
 * that records the size rather than measuring it back off the disk.
 */
object ImageDimensions {

    data class Size(val width: Int, val height: Int)

    /**
     * Whether a file of this media type is worth opening for a size.
     *
     * The same question is asked in SQL by `FileRepository.findImagesMissingDimensions`, which
     * cannot call this. The two are written to agree and name each other; change one, change
     * the other.
     *
     * Safe for everything these pages draw: a kind that is publicly readable admits only image
     * media types, so a picture on a page always answers true here.
     */
    fun mayHaveSize(mediaType: String): Boolean = mediaType.startsWith("image/")

    /** The size of a stored file, or nothing where it is missing or cannot be read. */
    fun of(path: Path): Size? =
        try {
            if (Files.exists(path)) Files.newInputStream(path).use(::of) else null
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

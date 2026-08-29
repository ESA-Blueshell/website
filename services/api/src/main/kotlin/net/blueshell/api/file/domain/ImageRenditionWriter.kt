package net.blueshell.api.file.domain

import net.blueshell.api.file.persistence.File
import net.blueshell.api.file.persistence.FileRepository
import org.slf4j.LoggerFactory
import org.springframework.beans.factory.annotation.Value
import org.springframework.stereotype.Component
import org.springframework.transaction.annotation.Transactional
import java.io.IOException
import java.nio.file.FileAlreadyExistsException
import java.nio.file.Files
import java.nio.file.Path
import java.nio.file.Paths
import java.nio.file.StandardCopyOption
import kotlin.math.max
import kotlin.math.roundToInt

/**
 * Writes the narrower copies a picture is served at.
 *
 * A picture is stored at the ladder of widths its kind lists, and at none wider than the
 * picture itself: nothing is upscaled, so a logo somebody uploaded at 200 pixels is offered at
 * 128 and at 200 and not at 256.
 *
 * A copy's address is its source's hash and the width, rather than a hash of the copy's own
 * bytes. Two things follow, and both are the point:
 *
 *  - a copy whose bytes have gone missing is written again to the address it always had, so a
 *    lost storage volume repairs itself rather than invalidating every url anybody cached;
 *  - upgrading the converter changes the bytes at addresses nobody is holding, because a
 *    rendition that already exists is never rewritten.
 *
 * Deriving is idempotent by construction: a width whose record and bytes are both there is
 * left alone, so this can be run on every upload and on every start.
 */
@Component
class ImageRenditionWriter(
    private val files: FileRepository,
    private val webpEncoder: WebpEncoder,
    @Value($$"${storage.location}") storageLocation: String,
) {
    private val root: Path = Paths.get(storageLocation)

    /**
     * The widths [source] should be stored at, written where they are not there already.
     *
     * Answers with the widths that now exist, which is every one its kind lists that is no
     * wider than the picture. A picture whose own size could not be read gets none: which
     * widths apply is decided by how wide it is, and guessing would either upscale it or
     * claim a width the bytes do not have.
     */
    @Transactional
    fun derive(source: File): List<File> {
        if (source.isRendition) return emptyList()
        val size = sizeOf(source) ?: return emptyList()
        val widths = source.type.renditionWidths.filter { it <= size.width }
        if (widths.isEmpty()) return emptyList()

        val master = root.resolve(source.path).normalize()
        if (!Files.exists(master)) {
            log.warn("[image-renditions] the bytes of {} are not in storage, so no width was written", source.path)
            return emptyList()
        }

        return widths.mapNotNull { width -> renditionOf(source, master, size, width) }
    }

    /**
     * One width, written where it is not there already.
     *
     * The record and the bytes are repaired independently. A record with no bytes behind it is
     * what a lost storage volume leaves, and bytes with no record are what a crash between the
     * two leaves; either alone is enough of a reason to encode, and neither is a reason to
     * refuse the others.
     */
    private fun renditionOf(source: File, master: Path, size: ImageDimensions.Size, width: Int): File? {
        val path = pathOf(source, width)
        val full = root.resolve(path).normalize()
        val existing = files.findByPath(path).orElse(null)
        val height = heightFor(size, width)

        if (!Files.exists(full)) {
            try {
                encode(source, master, full, ImageDimensions.Size(width, height))
            } catch (e: WebpConversionException) {
                log.warn("[image-renditions] the converter refused {} at {}px", source.path, width, e)
                return null
            } catch (e: IOException) {
                log.warn("[image-renditions] could not write {} at {}px: {}", source.path, width, e.message)
                return null
            }
        }

        if (existing != null) {
            existing.width = width
            existing.height = height
            return existing
        }

        return files.save(
            File(
                name = source.name,
                path = path,
                uploader = source.uploader,
                mediaType = WEBP_MEDIA_TYPE,
                size = runCatching { Files.size(full) }.getOrNull(),
                width = width,
                height = height,
                type = source.type,
                source = source,
                renditionWidth = width,
            ),
        )
    }

    /**
     * The bytes of one width, converted beside their destination and moved into place.
     *
     * Written to a temporary file first so that a reader never sees a half-encoded picture at
     * an address that promises the bytes there can never change. Losing the race to another
     * writer is not a failure: both were writing the same width of the same picture.
     */
    private fun encode(source: File, master: Path, destination: Path, size: ImageDimensions.Size) {
        Files.createDirectories(destination.parent)
        val scratch = Files.createTempFile(destination.parent, "rendition-", ".webp")
        var encoded = false
        try {
            webpEncoder.encode(
                input = master,
                output = scratch,
                quality = source.type.webpQuality,
                lossless = source.type.webpLossless,
                resize = size,
            )
            encoded = true
            try {
                Files.move(scratch, destination, StandardCopyOption.ATOMIC_MOVE)
            } catch (_: FileAlreadyExistsException) {
                Files.deleteIfExists(scratch)
            }
        } finally {
            if (!encoded) Files.deleteIfExists(scratch)
        }
    }

    /**
     * Where a width of a picture lives: the picture's own stored name, without its extension,
     * and the width. The stem is a hash of the picture's contents, so this is the hash and the
     * width, which is what makes the address stable for as long as the picture is.
     */
    private fun pathOf(source: File, width: Int): String {
        val name = source.path.substringAfterLast('/')
        val stem = name.substringBeforeLast('.', name)
        return "${source.type.directory}/$stem-$width.webp"
    }

    /** The height that keeps the picture's shape at [width], never rounded away to nothing. */
    private fun heightFor(size: ImageDimensions.Size, width: Int): Int =
        max(1, (size.height.toDouble() * width / size.width).roundToInt())

    /**
     * How large the picture is. The record is believed where it has an answer; a record that
     * was stored before sizes were recorded, and whose backfill has not reached it yet, is
     * measured here rather than skipped for a start.
     */
    private fun sizeOf(source: File): ImageDimensions.Size? {
        val width = source.width
        val height = source.height
        if (width != null && height != null && width > 0 && height > 0) {
            return ImageDimensions.Size(width, height)
        }
        return ImageDimensions.of(root.resolve(source.path).normalize())
    }

    private companion object {
        val log = LoggerFactory.getLogger(ImageRenditionWriter::class.java)
        const val WEBP_MEDIA_TYPE = "image/webp"
    }
}

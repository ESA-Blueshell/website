package net.blueshell.api.file.api

import net.blueshell.api.file.domain.ScratchSpace
import net.blueshell.api.file.domain.WebpConversionException
import net.blueshell.api.file.domain.WebpEncoder
import net.blueshell.api.file.persistence.File
import org.springframework.stereotype.Component
import java.awt.image.BufferedImage
import javax.imageio.ImageIO

/** A stored picture as a bitmap, for a document to draw. ImageIO has no reader for WebP, so that goes through `dwebp`. */
@Component
class PictureBitmaps(
    private val files: FileService,
    private val scratch: ScratchSpace,
    private val webp: WebpEncoder,
) {
    /** The picture, or null where it cannot be read as one. */
    fun bitmapOf(file: File): BufferedImage? {
        if (file.mediaType != WEBP) return files.openContent(file).use(ImageIO::read)
        return scratch.hold(files.openContent(file), ".webp").use { source ->
            scratch.cut(".png").use { png ->
                try {
                    webp.decode(source, png)
                } catch (_: WebpConversionException) {
                    return null
                }
                png.open().use(ImageIO::read)
            }
        }
    }

    private companion object {
        const val WEBP = "image/webp"
    }
}

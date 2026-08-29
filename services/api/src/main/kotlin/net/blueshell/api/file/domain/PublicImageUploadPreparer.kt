package net.blueshell.api.file.domain

import net.blueshell.api.shared.enums.FileType
import org.springframework.stereotype.Component
import java.nio.file.Files
import java.nio.file.Path

/** Prepares public page images for storage as capped WebP masters. */
@Component
class PublicImageUploadPreparer(
    private val webpEncoder: WebpEncoder,
) {
    data class Prepared(
        val path: Path,
        val mediaType: String,
        val width: Int,
        val height: Int,
    )

    fun prepare(source: Path, type: FileType): Prepared? {
        val maxEdge = type.maxImageEdge ?: return null
        val webpSize = WebpDimensions.of(source)
        if (webpSize != null && webpSize.longestEdge <= maxEdge) {
            return Prepared(source, WEBP_MEDIA_TYPE, webpSize.width, webpSize.height)
        }

        val sourceSize = webpSize
            ?: ImageDimensions.of(source)
            ?: throw InvalidFileException("The uploaded image could not be decoded")
        val targetSize = sourceSize.fittedWithin(maxEdge)
        val webp = Files.createTempFile(source.parent, "upload-image-", ".webp")
        var encoded = false
        try {
            webpEncoder.encode(
                input = source,
                output = webp,
                quality = type.webpQuality,
                lossless = type.webpLossless,
                resize = targetSize.takeIf { it != sourceSize },
            )
            encoded = true
            return Prepared(webp, WEBP_MEDIA_TYPE, targetSize.width, targetSize.height)
        } finally {
            if (!encoded) Files.deleteIfExists(webp)
        }
    }

    private companion object {
        const val WEBP_MEDIA_TYPE = "image/webp"
    }
}

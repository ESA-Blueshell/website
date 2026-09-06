package net.blueshell.api.file.domain

import net.blueshell.api.shared.enums.FileType
import org.springframework.stereotype.Component

/** Prepares public page images for storage as capped WebP masters. */
@Component
class PublicImageUploadPreparer(
    private val webpEncoder: WebpEncoder,
    private val scratch: ScratchSpace,
) {
    data class Prepared(
        val bytes: ScratchFile,
        val mediaType: String,
        val width: Int,
        val height: Int,
    )

    /**
     * Answers null for a kind that is stored as it was sent. Otherwise the WebP master, which
     * is [source] itself where it is already WebP and within the kind's ceiling — so the caller
     * must not assume the answer is a copy it may close independently.
     */
    fun prepare(source: ScratchFile, type: FileType): Prepared? {
        val maxEdge = type.maxImageEdge ?: return null
        val webpSize = source.open().use(WebpDimensions::of)
        if (webpSize != null && webpSize.longestEdge <= maxEdge) {
            return Prepared(source, WEBP_MEDIA_TYPE, webpSize.width, webpSize.height)
        }

        val sourceSize = webpSize
            ?: source.open().use(ImageDimensions::of)
            ?: throw InvalidFileException("The uploaded image could not be decoded")
        val targetSize = sourceSize.fittedWithin(maxEdge)
        val webp = scratch.cut(".webp")
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
            if (!encoded) webp.close()
        }
    }

    private companion object {
        const val WEBP_MEDIA_TYPE = "image/webp"
    }
}

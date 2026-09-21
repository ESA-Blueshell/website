package net.blueshell.api.file.domain

import net.blueshell.api.shared.enums.FileType
import org.springframework.stereotype.Component

/** Prepares public page images for storage as capped WebP masters. */
@Component
class PublicImageUploadPreparer(
    private val webpEncoder: WebpEncoder,
    private val scratch: ScratchSpace,
    private val animated: AnimatedImages,
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
     *
     * A picture of more than one frame keeps them. [declaredMediaType] says which decoder to
     * try; it is the uploader's claim, so a file that turns out to hold one frame falls through
     * to the ordinary converter rather than being refused.
     */
    fun prepare(
        source: ScratchFile,
        type: FileType,
        declaredMediaType: String,
    ): Prepared? {
        val maxEdge = type.maxImageEdge ?: return null
        val webpSize = source.open().use(WebpDimensions::of)
        if (webpSize != null && webpSize.longestEdge <= maxEdge) {
            return Prepared(source, WEBP_MEDIA_TYPE, webpSize.width, webpSize.height)
        }

        if (animated.mayAnimate(declaredMediaType)) {
            animation(source, type, declaredMediaType, maxEdge)?.let { return it }
        }

        // A GIF of one frame is still a GIF, which the still converter will not read.
        val readable = animated.readableStillOf(source, declaredMediaType)
        try {
            val input = readable ?: source
            val sourceSize =
                webpSize
                    ?: input.open().use(ImageDimensions::of)
                    ?: throw InvalidFileException("The uploaded image could not be decoded")
            return still(input, type, sourceSize, sourceSize.fittedWithin(maxEdge))
        } finally {
            readable?.close()
        }
    }

    /**
     * The master of a picture with more than one frame, or nothing where it has only one.
     *
     * Every frame is brought under the ceiling, not just the first: a banner that animates is
     * still a banner, and half a ladder of full-size frames is what the ceiling exists to stop.
     */
    private fun animation(
        source: ScratchFile,
        type: FileType,
        declaredMediaType: String,
        maxEdge: Int,
    ): Prepared? =
        animated.framesOf(source, declaredMediaType)?.use { frames ->
            val targetSize = frames.size.fittedWithin(maxEdge)
            val webp = scratch.cut(".webp")
            var encoded = false
            try {
                animated.write(
                    frames = frames,
                    output = webp,
                    quality = type.webpQuality,
                    lossless = type.webpLossless,
                    size = targetSize.takeIf { it != frames.size },
                )
                encoded = true
                Prepared(webp, WEBP_MEDIA_TYPE, targetSize.width, targetSize.height)
            } finally {
                if (!encoded) webp.close()
            }
        }

    private fun still(
        source: ScratchFile,
        type: FileType,
        sourceSize: ImageDimensions.Size,
        targetSize: ImageDimensions.Size,
    ): Prepared {
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

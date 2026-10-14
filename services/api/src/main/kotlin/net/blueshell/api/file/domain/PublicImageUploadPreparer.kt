package net.blueshell.api.file.domain

import net.blueshell.api.shared.enums.FileType
import org.springframework.stereotype.Component

/** Prepares public page images for storage as capped WebP masters. */
@Component
class PublicImageUploadPreparer(
    private val converter: ImageConverter,
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

        val master = converter.master(source, declaredMediaType, type)
        return Prepared(master.bytes, WEBP_MEDIA_TYPE, master.size.width, master.size.height)
    }

    private companion object {
        const val WEBP_MEDIA_TYPE = "image/webp"
    }
}

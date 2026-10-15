package net.blueshell.api.file.domain

import net.blueshell.api.file.api.BlobStore
import net.blueshell.api.file.persistence.File
import net.blueshell.api.shared.job.JobQueue
import net.blueshell.api.shared.job.JobTrigger
import org.springframework.stereotype.Component

/**
 * Writes a still's widths now and queues an animation's, which costs a conversion per frame per
 * width. Nothing is part-written meanwhile: a width's key is written once, so a still would stick.
 */
@Component
class ImageRenditions(
    private val writer: ImageRenditionWriter,
    private val blobs: BlobStore,
    private val jobs: JobQueue,
) {
    fun request(source: File): List<File> {
        if (!moves(source)) return writer.derive(source)
        source.id?.let { id -> jobs.runAsync(ImageJobs.DeriveRenditions, ImageJobs.DeriveRenditionsPayload(id), JobTrigger.SITE_ACTION) }
        return emptyList()
    }

    /** From the header: a WebP's flag, or a legacy GIF row taken at its word. */
    private fun moves(source: File): Boolean {
        if (!blobs.exists(source.path)) return false
        return when (source.mediaType.substringBefore(';').trim()) {
            "image/gif" -> true
            "image/webp" -> blobs.open(source.path).use(WebpDimensions::isAnimated)
            else -> false
        }
    }
}

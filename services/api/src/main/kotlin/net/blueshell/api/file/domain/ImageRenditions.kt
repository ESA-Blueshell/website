package net.blueshell.api.file.domain

import net.blueshell.api.file.api.BlobStore
import net.blueshell.api.file.persistence.File
import net.blueshell.api.shared.job.JobQueue
import net.blueshell.api.shared.job.JobTrigger
import org.springframework.stereotype.Component

/**
 * Asks for the widths a picture is served at, and decides who does the work.
 *
 * A still is one conversion per width, which is quick enough to do while the caller waits. An
 * animation is a decode and an encode per frame per width, which is not: a banner of two hundred frames
 * would hold a start open or leave somebody watching an upload spinner for minutes. So it is
 * queued, on its own job row, where it can fail and be retried and be read about.
 *
 * Nothing part-written in the meantime. A width is addressed by its source's hash, and the
 * store writes a key once, so a still ladder put down now would be the ladder for good and the
 * animation would never replace it. Until the job runs, a page is served the master.
 */
@Component
class ImageRenditions(
    private val writer: ImageRenditionWriter,
    private val blobs: BlobStore,
    private val jobs: JobQueue,
) {
    /**
     * The widths of [source] that now exist: every one of them for a still, and none yet for a
     * picture that moves, which is queued instead.
     */
    fun request(source: File): List<File> {
        if (!moves(source)) return writer.derive(source)
        source.id?.let { id -> jobs.runAsync(ImageJobs.DeriveRenditions, ImageJobs.DeriveRenditionsPayload(id), JobTrigger.SITE_ACTION) }
        return emptyList()
    }

    /**
     * Whether this picture has frames to replay, asked of its header rather than the converter.
     *
     * A WebP answers from its container flags. A GIF has no such flag, and only the rows stored
     * before WebP masters hold one, so a GIF is taken at its word: the service draws one of a
     * single frame as a still anyway.
     *
     * A picture whose bytes are gone has none. It takes the ordinary path, where the writer
     * reports the missing bytes in its own words rather than a job failing to find them.
     */
    private fun moves(source: File): Boolean {
        if (!blobs.exists(source.path)) return false
        return when (source.mediaType.substringBefore(';').trim()) {
            "image/gif" -> true
            "image/webp" -> blobs.open(source.path).use(WebpDimensions::isAnimated)
            else -> false
        }
    }
}

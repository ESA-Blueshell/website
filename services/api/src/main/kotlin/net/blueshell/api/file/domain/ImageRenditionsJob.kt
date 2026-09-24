package net.blueshell.api.file.domain

import net.blueshell.api.file.persistence.FileRepository
import net.blueshell.api.jobs.api.AbstractJsonJobHandler
import net.blueshell.api.shared.job.ImageJobs
import org.springframework.stereotype.Component
import tools.jackson.databind.ObjectMapper

/**
 * The widths of one picture, derived away from whatever asked for them.
 *
 * Queued for an animation, which costs a converter run per frame per width: on the startup path
 * that is a start held open, and on the upload path a request held open. One job per picture, so
 * a banner the converter will not take fails on its own row and is retried and read there
 * instead of taking a whole sweep down with it.
 *
 * A picture that has since been deleted is skipped, not failed. The job was queued against a
 * record that no longer exists, and retrying it would never find one.
 */
@Component
class ImageRenditionsJob(
    objectMapper: ObjectMapper,
    private val files: FileRepository,
    private val renditions: ImageRenditionWriter,
) : AbstractJsonJobHandler<ImageJobs.DeriveRenditionsPayload>(
        objectMapper,
        ImageJobs.DeriveRenditions.payloadType,
    ) {
    override val jobType: String = ImageJobs.DeriveRenditions.type

    override fun handlePayload(payload: ImageJobs.DeriveRenditionsPayload) {
        val source = files.findById(payload.fileId).orElse(null) ?: return skip("The picture has been deleted.")
        renditions.widthsOf(source).none?.let(::skip)
    }
}

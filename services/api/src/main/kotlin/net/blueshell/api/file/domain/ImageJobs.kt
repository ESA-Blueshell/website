package net.blueshell.api.file.domain

import net.blueshell.api.shared.job.JobDefinition

/**
 * A picture's own widths, derived away from whatever asked for them.
 *
 * Only an animation is queued. A still costs one converter run per width and is written where
 * it was asked for; an animation costs one per frame per width, which is not something a start
 * or a request should wait on.
 */
object ImageJobs {
    object DeriveRenditions : JobDefinition<DeriveRenditionsPayload> {
        override val type: String = "image.derive-renditions"
        override val payloadType: Class<DeriveRenditionsPayload> = DeriveRenditionsPayload::class.java
    }

    /** The picture's own id: its widths are derived from whatever the record points at now. */
    data class DeriveRenditionsPayload(
        val fileId: Long,
    )
}

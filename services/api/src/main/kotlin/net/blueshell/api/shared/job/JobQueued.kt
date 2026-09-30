package net.blueshell.api.shared.job

import net.blueshell.api.shared.tracking.Actor

/**
 * A job was written to the queue, inside the transaction that queued it. A listener that records
 * something about the job does so in that same transaction; it runs before the job can.
 */
data class JobQueued(
    val executionId: Long,
    val jobType: String,
    val payload: String?,
    /** Who queued it: the person whose action it follows, or the system. */
    val actor: Actor,
)

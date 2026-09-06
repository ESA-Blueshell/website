package net.blueshell.api.shared.job

import net.blueshell.api.shared.tracking.Actor
import net.blueshell.api.shared.tracking.ActorTracked

/**
 * Job queue interface for dispatching asynchronous jobs.
 * Domain services use this interface without depending on the platform implementation.
 *
 * Aligned with ADR-019 (Anti-Corruption Layers).
 */
interface JobQueue {
    /**
     * Runs a job with a typed payload asynchronously and durably, attributed to the
     * current actor.
     * Returns a job execution tracking object, or null if dedup suppressed the job.
     */
    fun <T : Any> runAsync(job: JobDefinition<T>, payload: T): QueuedJob? =
        runAsync(job, payload, null)

    /**
     * Runs a job attributed to [actor]; a null actor is resolved to the current one by
     * the implementation.
     */
    fun <T : Any> runAsync(job: JobDefinition<T>, payload: T, actor: Actor?): QueuedJob?
}

/** Queues a job on behalf of whoever the tracked thing records as its actor. */
fun <T : Any> JobQueue.runAsyncFromActor(
    job: JobDefinition<T>,
    payload: T,
    actor: ActorTracked
): QueuedJob? = runAsync(job, payload, actor.actor)

/**
 * A job that has been accepted by the queue and may still be executing.
 * Minimal interface for domain layer - platform layer can extend this.
 */
interface QueuedJob : ActorTracked {
    val id: Long?
    val jobType: String
    val payload: String?
}

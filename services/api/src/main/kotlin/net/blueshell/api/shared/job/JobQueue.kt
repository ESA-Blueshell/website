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
     * Runs a job with a typed payload asynchronously and durably, queued because of [trigger] and
     * attributed to [actor], or to the current actor where it is null. Returns the job's execution:
     * a new one, or its queued twin, which it is folded into; null where a running twin makes it
     * redundant.
     */
    fun <T : Any> runAsync(
        job: JobDefinition<T>,
        payload: T,
        trigger: JobTrigger,
        actor: Actor? = null,
    ): QueuedJob?
}

/** Queues a job on behalf of whoever the tracked thing records as its actor. */
fun <T : Any> JobQueue.runAsyncFromActor(
    job: JobDefinition<T>,
    payload: T,
    trigger: JobTrigger,
    actor: ActorTracked,
): QueuedJob? = runAsync(job, payload, trigger, actor.actor)

/**
 * A job that has been accepted by the queue and may still be executing.
 * Minimal interface for domain layer - platform layer can extend this.
 */
interface QueuedJob : ActorTracked {
    val id: Long?
    val jobType: String
    val payload: String?
}

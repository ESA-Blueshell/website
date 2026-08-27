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
     * Runs a job with a typed payload asynchronously and durably.
     * Returns a job execution tracking object, or null if dedup suppressed the job.
     */
    fun <T : Any> runAsync(
        job: JobDefinition<T>,
        payload: T,
        actor: Actor? = null
    ): JobExecution?

    /**
     * Runs a job by type name with optional payload asynchronously and durably.
     * Returns a job execution tracking object, or null if dedup suppressed the job.
     */
    fun runAsync(
        jobType: String,
        payload: Any? = null,
        actor: Actor? = null,
        dedupKey: String? = null
    ): JobExecution?
}

/**
 * Represents a queued or executing job.
 * Minimal interface for domain layer - platform layer can extend this.
 */
interface JobExecution : ActorTracked {
    val id: Long?
    val jobType: String
    val payload: String?
}

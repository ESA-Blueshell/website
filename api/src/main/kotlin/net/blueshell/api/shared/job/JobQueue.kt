package net.blueshell.api.shared.job

import net.blueshell.api.shared.tracking.Actor
import net.blueshell.api.shared.tracking.ActorTracked

/**
 * Job queue interface for dispatching asynchronous jobs.
 * Domain services use this interface without depending on the platform implementation (RabbitMQ).
 *
 * Aligned with ADR-019 (Anti-Corruption Layers).
 */
interface JobQueue {
    /**
     * Enqueues a job with a typed payload.
     * Returns a job execution tracking object.
     */
    fun <T : Any> enqueue(
        job: JobDefinition<T>,
        payload: T,
        actor: Actor? = null
    ): JobExecution

    /**
     * Enqueues a job by type name with optional payload.
     */
    fun enqueue(
        jobType: String,
        payload: Any? = null,
        actor: Actor? = null
    ): JobExecution
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

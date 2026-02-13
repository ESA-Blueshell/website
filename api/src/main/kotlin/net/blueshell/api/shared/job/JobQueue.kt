package net.blueshell.api.shared.job

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
    fun <T : Any> enqueue(job: JobDefinition<T>, payload: T): JobExecution

    /**
     * Enqueues a job by type name with optional payload.
     */
    fun enqueue(jobType: String, payload: Any? = null): JobExecution
}

/**
 * Represents a queued or executing job.
 * Minimal interface for domain layer - platform layer can extend this.
 */
interface JobExecution {
    val id: Long?
    val jobType: String
    val payload: String?
}

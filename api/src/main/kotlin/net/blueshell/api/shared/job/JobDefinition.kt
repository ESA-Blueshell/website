package net.blueshell.api.shared.job

/**
 * Defines a type of job that can be dispatched to the job queue.
 * Domain layer uses this to define job types without depending on platform implementation.
 */
interface JobDefinition<T : Any> {
    val type: String
    val payloadType: Class<T>

    /**
     * Computes an optional dedup key from the payload.
     * If non-null and an active job (QUEUED or RUNNING) exists with the same job type and dedup key,
     * the new job will be suppressed.
     */
    fun dedupKey(payload: T): String? = null
}

package net.blueshell.api.shared.job

import java.security.MessageDigest

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
     *
     * Default implementation hashes the fully-qualified class name and toString() of the payload,
     * which for data classes covers all fields automatically.
     */
    fun dedupKey(payload: T): String? = payloadHash(payload)
}

private fun <T : Any> payloadHash(payload: T): String {
    val input = "${payload.javaClass.name}|${payload}"
    val digest = MessageDigest.getInstance("SHA-256").digest(input.toByteArray())
    return digest.joinToString("") { "%02x".format(it) }
}

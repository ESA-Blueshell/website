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
     * If non-null and an active job exists with the same job type and dedup key,
     * the new job will be suppressed. [coalesceAgainstQueuedOnly] controls which
     * statuses count as "active".
     *
     * Default implementation hashes the fully-qualified class name and toString() of the payload,
     * which for data classes covers all fields automatically.
     */
    fun dedupKey(payload: T): String? = payloadHash(payload)

    /**
     * When `false` (default), an enqueue is suppressed if any job with the same
     * dedup key is QUEUED or RUNNING.
     *
     * When `true`, the in-flight RUNNING job no longer blocks enqueue; only a
     * pre-existing QUEUED job does. This implements queue-tail coalescing: an
     * upstream change that arrives mid-run becomes the next run rather than
     * being silently dropped. The handler must be self-reconciling — i.e. read
     * authoritative state inside its own transaction — otherwise back-to-back
     * runs can still issue redundant external calls.
     */
    val coalesceAgainstQueuedOnly: Boolean get() = false
}

private fun <T : Any> payloadHash(payload: T): String {
    val input = "${payload.javaClass.name}|${payload}"
    val digest = MessageDigest.getInstance("SHA-256").digest(input.toByteArray())
    return digest.joinToString("") { "%02x".format(it) }
}

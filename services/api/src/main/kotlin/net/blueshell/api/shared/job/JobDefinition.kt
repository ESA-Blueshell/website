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
     * A dedup key for the payload: a new job is suppressed where one of the same type and key is
     * already QUEUED or RUNNING. Hashes the class name and `toString()` by default, which covers
     * every field of a data class.
     */
    fun dedupKey(payload: T): String? = payloadHash(payload)
}

private fun <T : Any> payloadHash(payload: T): String {
    val input = "${payload.javaClass.name}|${payload}"
    val digest = MessageDigest.getInstance("SHA-256").digest(input.toByteArray())
    return digest.joinToString("") { "%02x".format(it) }
}

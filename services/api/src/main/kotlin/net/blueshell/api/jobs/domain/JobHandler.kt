package net.blueshell.api.jobs.domain

import net.blueshell.api.jobs.api.RetrySchedule

interface JobHandler {
    val jobType: String

    /** The payload class this handler deserializes into; drives the manual-trigger catalog. */
    val payloadType: Class<*>

    /** Null retries on the queue's own schedule. */
    val retrySchedule: RetrySchedule?
        get() = null

    fun handle(
        payload: String?,
        executionId: Long? = null,
    )
}

package net.blueshell.api.jobs.domain

import net.blueshell.api.jobs.api.JobOutcome
import net.blueshell.api.jobs.api.RetrySchedule

interface JobHandler {
    val jobType: String

    /** The payload class this handler deserializes into; drives the manual-trigger catalog. */
    val payloadType: Class<*>

    /** Null retries on the queue's own schedule. */
    val retrySchedule: RetrySchedule?
        get() = null

    /**
     * [forced] is a run somebody asked for by hand: a handler does what it would otherwise wait
     * for, and skips only where the work cannot be done at all.
     */
    fun handle(
        payload: String?,
        executionId: Long?,
        forced: Boolean,
    ): JobOutcome
}

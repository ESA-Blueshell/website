package net.blueshell.api.jobs.domain

interface JobHandler {
    val jobType: String

    /** The payload class this handler deserializes into; drives the manual-trigger catalog. */
    val payloadType: Class<*>

    fun handle(payload: String?, executionId: Long? = null)
}

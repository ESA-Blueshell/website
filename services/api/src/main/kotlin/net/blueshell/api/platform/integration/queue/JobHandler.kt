package net.blueshell.api.platform.integration.queue

interface JobHandler {
    val jobType: String

    /** The payload class this handler deserializes into; drives the manual-trigger catalog. */
    val payloadType: Class<*>

    fun handle(payload: String?, executionId: Long? = null)
}

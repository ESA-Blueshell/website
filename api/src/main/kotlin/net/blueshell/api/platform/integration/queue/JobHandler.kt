package net.blueshell.api.platform.integration.queue

interface JobHandler {
    val jobType: String
    fun handle(payload: String?)
}

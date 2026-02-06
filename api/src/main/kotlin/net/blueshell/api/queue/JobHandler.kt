package net.blueshell.api.queue

interface JobHandler {
    val jobType: String
    fun handle(payload: String?)
}

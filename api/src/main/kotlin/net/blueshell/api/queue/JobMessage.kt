package net.blueshell.api.queue

data class JobMessage(
    val executionId: Long,
    val jobType: String,
    val payload: String? = null
)

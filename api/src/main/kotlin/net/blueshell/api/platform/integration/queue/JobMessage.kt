package net.blueshell.api.platform.integration.queue

data class JobMessage(
    val executionId: Long,
    val jobType: String,
    val payload: String? = null
)

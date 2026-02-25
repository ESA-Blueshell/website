package net.blueshell.api.shared.job

class NonRetryableJobException(
    message: String,
    cause: Throwable? = null
) : RuntimeException(message, cause)

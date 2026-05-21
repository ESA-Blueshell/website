package net.blueshell.api.shared.job

import jakarta.validation.ConstraintViolationException

class NonRetryableJobException(
    message: String,
    cause: Throwable? = null
) : RuntimeException(message, cause) {

    companion object {
        /**
         * Canonical set of exception types that should never be retried.
         * [net.blueshell.api.platform.integration.queue.JobExecutor] inspects this
         * set to decide whether a failed attempt should be marked DEAD immediately
         * or scheduled for an exponential-backoff retry.
         */
        val NON_RETRYABLE_EXCEPTIONS: Set<Class<out Throwable>> = setOf(
            NonRetryableJobException::class.java,
            ConstraintViolationException::class.java,
            IllegalArgumentException::class.java,
            NullPointerException::class.java,
            ClassCastException::class.java,
        )
    }
}

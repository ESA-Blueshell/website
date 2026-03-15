package net.blueshell.api.shared.job

import jakarta.validation.ConstraintViolationException

class NonRetryableJobException(
    message: String,
    cause: Throwable? = null
) : RuntimeException(message, cause) {

    companion object {
        /**
         * Canonical set of exception types that should never be retried.
         * Both [net.blueshell.api.platform.config.JobRetryConfig] and
         * [net.blueshell.api.platform.integration.queue.JobExecutor] derive from this set.
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

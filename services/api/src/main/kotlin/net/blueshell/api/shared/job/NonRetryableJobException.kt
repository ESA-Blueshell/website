package net.blueshell.api.shared.job

import jakarta.validation.ConstraintViolationException

open class NonRetryableJobException(
    message: String,
    cause: Throwable? = null
) : RuntimeException(message, cause) {

    companion object {
        /**
         * Exception types a retry cannot help, which `JobExecutor` marks FAILED at once rather
         * than scheduling for backoff. Bad input belongs here, and so do coercion bugs: those
         * crash on every redelivery until a deploy fixes them, and a terminal failure shows on
         * the dashboard instead of being buried in silent backoff. DEAD is a different thing —
         * the job system cannot run this at all, such as an unregistered job type.
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

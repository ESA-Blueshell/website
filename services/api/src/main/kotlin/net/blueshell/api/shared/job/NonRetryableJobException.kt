package net.blueshell.api.shared.job

import jakarta.validation.ConstraintViolationException

open class NonRetryableJobException(
    message: String,
    cause: Throwable? = null
) : RuntimeException(message, cause) {

    companion object {
        /**
         * Canonical set of exception types that should never be retried.
         * [net.blueshell.api.jobs.api.JobExecutor] inspects this
         * set to decide whether a failed attempt should be marked FAILED
         * immediately (no further attempts) or scheduled for an
         * exponential-backoff retry.
         *
         * Membership in this set means "retrying will not change the outcome",
         * which is true for validation / bad-input errors AND for code-level
         * type-coercion / null-coercion bugs — those will keep crashing on
         * every redelivery until a deploy fixes the code. We mark them FAILED
         * (terminal) so they show up loudly on the dashboard and an operator
         * can hit Retry after the fix lands, rather than burying them via
         * silent exponential backoff.
         *
         * DEAD is reserved for "the job system itself cannot run this" —
         * e.g. no handler is registered for the job type.
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

package net.blueshell.api.shared.job

/**
 * A failure whose message is a sentence written for whoever reads the jobs page, so it is shown
 * without its type in front. Retried like any other failure.
 */
open class ExplainedJobFailure(
    message: String,
    cause: Throwable? = null,
) : RuntimeException(message, cause)

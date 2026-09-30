package net.blueshell.api.exceptions.api

/** Where an exception surfaced: a request, or a job the queue ran. */
enum class ExceptionSource {
    REQUEST,
    JOB,
}

/**
 * What was running when an exception was thrown: a request's route such as `GET /events/{id}`,
 * or a job's type and the execution that failed.
 */
data class ExceptionConcern(
    val source: ExceptionSource,
    val label: String,
    val jobExecutionId: Long?,
)

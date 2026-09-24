package net.blueshell.api.jobs.api

/** How a run that raised no error ended: its work done, or nothing done for a reason an operator reads. */
sealed interface JobOutcome {
    data object Done : JobOutcome

    data class Skipped(
        val reason: String,
    ) : JobOutcome
}

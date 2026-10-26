package net.blueshell.api.jobs.api

import net.blueshell.api.shared.job.JobCount
import net.blueshell.api.shared.job.JobEffect

/** How a run that raised no error ended: its work done, or nothing done for a reason an operator reads. */
sealed interface JobOutcome {
    /** Its work done: [effect] is what that did to the thing it keeps, where it keeps one, and [counts] what it counted. */
    data class Done(
        val effect: JobEffect? = null,
        val link: String? = null,
        val counts: List<JobCount> = emptyList(),
    ) : JobOutcome

    data class Skipped(
        val reason: String,
    ) : JobOutcome
}

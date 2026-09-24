package net.blueshell.api.testsupport

import net.blueshell.api.jobs.api.JobOutcome
import net.blueshell.api.jobs.domain.JobHandler

/** One run of a handler as the executor makes it, for a test with no execution to name. */
fun JobHandler.runJob(
    payload: String?,
    executionId: Long? = null,
    forced: Boolean = false,
): JobOutcome = handle(payload, executionId, forced)

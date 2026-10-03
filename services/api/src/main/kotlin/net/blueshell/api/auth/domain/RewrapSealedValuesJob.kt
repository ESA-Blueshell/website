package net.blueshell.api.auth.domain

import net.blueshell.api.jobs.api.AbstractJsonJobHandler
import net.blueshell.api.shared.job.ExplainedJobFailure
import net.blueshell.api.user.api.SealedValueRewrap
import net.blueshell.api.user.api.UserJobs
import org.springframework.stereotype.Component
import tools.jackson.databind.ObjectMapper

/**
 * Moves sealed values onto the newest version of their key. Here rather than in `user`, which
 * seals, for the reason [SealAddressesJob] is. What it moved stays moved when it fails: the
 * failure only records the values left behind, which the next run tries again.
 */
@Component
class RewrapSealedValuesJob(
    objectMapper: ObjectMapper,
    private val rewrap: SealedValueRewrap,
) : AbstractJsonJobHandler<UserJobs.RewrapSealedValuesPayload>(objectMapper, UserJobs.RewrapSealedValues) {
    override fun handlePayload(payload: UserJobs.RewrapSealedValuesPayload) {
        val report = rewrap.rewrapEvery()
        if (report.failed.isNotEmpty()) {
            val more = (report.failed.size - NAMED).takeIf { it > 0 }?.let { " and $it more" }.orEmpty()
            throw ExplainedJobFailure(
                "Sealed values left on an older key version: ${report.failed.take(
                    NAMED,
                ).joinToString(", ")}$more. The next run tries them again.",
            )
        }
        if (report.moved == 0) skip("Every sealed value is on the newest key version")
    }

    private companion object {
        const val NAMED = 10
    }
}

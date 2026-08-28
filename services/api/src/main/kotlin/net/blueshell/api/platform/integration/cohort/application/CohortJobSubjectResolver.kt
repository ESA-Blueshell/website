package net.blueshell.api.platform.integration.cohort.application

import net.blueshell.api.platform.integration.cohort.persistence.repository.CohortRepository
import net.blueshell.api.jobs.web.JobSubjectResolver
import org.springframework.core.annotation.Order
import org.springframework.stereotype.Component

@Component
@Order(50)
class CohortJobSubjectResolver(
    private val cohorts: CohortRepository,
) : JobSubjectResolver {
    override val payloadFields = listOf("cohortId")
    override val entityType = "COHORT"

    override fun label(id: Long): String {
        val cohort = cohorts.findById(id).orElse(null) ?: return "Cohort #$id"
        return "${cohort.label} (${cohort.system} ${cohort.kind})"
    }
}

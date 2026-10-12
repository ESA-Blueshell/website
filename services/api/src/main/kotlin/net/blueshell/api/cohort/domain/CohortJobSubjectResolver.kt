package net.blueshell.api.cohort.domain

import net.blueshell.api.cohort.persistence.TargetRepository
import net.blueshell.api.jobs.api.JobSubjectResolver
import org.springframework.core.annotation.Order
import org.springframework.stereotype.Component

@Component
@Order(50)
class CohortJobSubjectResolver(
    private val targets: TargetRepository,
) : JobSubjectResolver {
    override val payloadFields = listOf("cohortId")
    override val entityType = "COHORT"

    override fun label(id: Long): String {
        val target = targets.findById(id).orElse(null) ?: return "Target #$id"
        return "${target.label} (${target.system} ${target.kind})"
    }
}

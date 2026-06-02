package net.blueshell.api.platform.integration.cohort.application

import net.blueshell.api.platform.integration.cohort.persistence.Cohort
import net.blueshell.api.platform.integration.cohort.persistence.repository.CohortMemberRepository
import net.blueshell.api.platform.integration.cohort.persistence.repository.CohortRepository
import org.springframework.stereotype.Service
import org.springframework.transaction.annotation.Transactional

/**
 * Read-side application service: lists cohorts for the admin UI
 * pickers and inspection pages. The controller depends on this
 * (per platform-architecture rules controllers must not access
 * repositories directly).
 */
@Service
class CohortQueryService(
    private val cohorts: CohortRepository,
    private val cohortMembers: CohortMemberRepository,
) {
    @Transactional(readOnly = true)
    fun summaries(): List<CohortSummary> =
        cohorts.findAll().map { cohort ->
            CohortSummary(
                cohort = cohort,
                memberCount = cohortMembers.findAllByCohortId(cohort.id!!).size,
            )
        }
}

/** Read-model projection of a [Cohort] for admin listings. */
data class CohortSummary(
    val cohort: Cohort,
    val memberCount: Int,
)

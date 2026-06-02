package net.blueshell.api.platform.integration.cohort.persistence.repository

import net.blueshell.api.platform.integration.cohort.persistence.CohortFactKind
import net.blueshell.api.platform.integration.cohort.persistence.CohortRule
import net.blueshell.api.shared.repository.BaseRepository
import org.springframework.stereotype.Repository

@Repository
interface CohortRuleRepository : BaseRepository<CohortRule, Long> {
    fun findAllByEnabledTrue(): List<CohortRule>

    fun findAllByFactKindAndFactKeyAndEnabledTrue(
        factKind: CohortFactKind,
        factKey: String,
    ): List<CohortRule>

    fun findAllByCohortId(cohortId: Long): List<CohortRule>

    fun findAllBySubjectId(subjectId: Long): List<CohortRule>
}

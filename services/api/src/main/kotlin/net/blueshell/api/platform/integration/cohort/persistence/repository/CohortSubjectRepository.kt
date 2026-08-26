package net.blueshell.api.platform.integration.cohort.persistence.repository

import net.blueshell.api.platform.integration.cohort.persistence.CohortSubject
import net.blueshell.api.platform.integration.cohort.persistence.CohortSubjectType
import net.blueshell.api.shared.repository.BaseRepository
import org.springframework.stereotype.Repository

@Repository
interface CohortSubjectRepository : BaseRepository<CohortSubject, Long> {
    fun findAllByType(type: CohortSubjectType): List<CohortSubject>

    /** The subject produced by one definition — unique key uk_cohort_subject_definition. */
    fun findByDefinitionKey(definitionKey: String): CohortSubject?
}

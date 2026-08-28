package net.blueshell.api.cohort.persistence

import net.blueshell.api.shared.repository.BaseRepository
import org.springframework.stereotype.Repository

@Repository
interface CohortSubjectRepository : BaseRepository<CohortSubject, Long> {
    fun findAllByType(type: CohortSubjectType): List<CohortSubject>

    /** The subject produced by one definition — unique key uk_cohort_subject_definition. */
    fun findByDefinitionKey(definitionKey: String): CohortSubject?
}

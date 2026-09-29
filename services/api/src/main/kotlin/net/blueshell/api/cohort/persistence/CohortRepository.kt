package net.blueshell.api.cohort.persistence

import net.blueshell.api.shared.repository.BaseRepository
import org.springframework.stereotype.Repository

@Repository
interface CohortRepository : BaseRepository<Cohort, Long> {
    fun findAllByType(type: CohortType): List<Cohort>

    /** The cohort produced by one definition — unique key uk_cohort_subject_definition. */
    fun findByDefinitionKey(definitionKey: String): Cohort?
}

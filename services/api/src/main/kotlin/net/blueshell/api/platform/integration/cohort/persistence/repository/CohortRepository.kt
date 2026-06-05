package net.blueshell.api.platform.integration.cohort.persistence.repository

import net.blueshell.api.platform.integration.cohort.persistence.Cohort
import net.blueshell.api.platform.integration.cohort.persistence.CohortFactKind
import net.blueshell.api.platform.integration.cohort.persistence.CohortKind
import net.blueshell.api.shared.repository.BaseRepository
import org.springframework.data.jpa.repository.Query
import org.springframework.stereotype.Repository

/**
 * `system` is a plain string holding a `TargetSystem.name()`; the
 * persistence layer cannot depend on the `sync.port` package.
 * Application code resolves the enum.
 */
@Repository
interface CohortRepository : BaseRepository<Cohort, Long> {
    fun findAllBySystem(system: String): List<Cohort>

    fun findAllBySystemAndKind(system: String, kind: CohortKind): List<Cohort>

    fun findAllBySubjectId(subjectId: Long): List<Cohort>

    fun countBySubjectId(subjectId: Long): Long

    fun findBySubjectIdAndSystem(subjectId: Long, system: String): Cohort?

    /** Active cohort already owning [externalId] on [system], if any (CohortTargetIds uniqueness guard). */
    fun findFirstBySystemAndExternalId(system: String, externalId: String): Cohort?

    /**
     * Active cohorts whose subject's rule matches the held fact `(factKind, factKey)` and is enabled.
     * The `@SQLRestriction` on both entities keeps the join to non-deleted rows; the evaluator uses
     * this in place of the retired `cohort_rule` lookup.
     */
    @Query(
        """
        SELECT c FROM Cohort c, CohortSubject s
        WHERE s.id = c.subjectId
          AND s.factKind = :factKind
          AND s.factKey = :factKey
          AND s.enabled = true
        """,
    )
    fun findAllForEnabledSubjectFact(factKind: CohortFactKind, factKey: String): List<Cohort>
}

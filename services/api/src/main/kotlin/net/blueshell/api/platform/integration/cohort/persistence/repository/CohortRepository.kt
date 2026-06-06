package net.blueshell.api.platform.integration.cohort.persistence.repository

import net.blueshell.api.platform.integration.cohort.persistence.Cohort
import net.blueshell.api.platform.integration.cohort.persistence.CohortFactKind
import net.blueshell.api.platform.integration.cohort.persistence.CohortKind
import net.blueshell.api.shared.enums.TargetSystem
import net.blueshell.api.shared.repository.BaseRepository
import org.springframework.data.jpa.repository.Query
import org.springframework.stereotype.Repository

/**
 * `Cohort.system` is the [TargetSystem] enum, persisted via
 * `@Enumerated(STRING)`. Every `system` lookup binds the enum directly —
 * Hibernate rejects a `String` argument against the enum-typed attribute.
 */
@Repository
interface CohortRepository : BaseRepository<Cohort, Long> {
    fun findAllBySystem(system: TargetSystem): List<Cohort>

    fun findAllBySystemAndKind(system: TargetSystem, kind: CohortKind): List<Cohort>

    fun findAllBySubjectId(subjectId: Long): List<Cohort>

    fun countBySubjectId(subjectId: Long): Long

    fun findBySubjectIdAndSystem(subjectId: Long, system: TargetSystem): Cohort?

    /** Active cohort already owning [externalId] on [system], if any (CohortTargetIds uniqueness guard). */
    fun findFirstBySystemAndExternalId(system: TargetSystem, externalId: String): Cohort?

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

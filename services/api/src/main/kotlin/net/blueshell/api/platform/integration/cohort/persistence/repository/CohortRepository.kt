package net.blueshell.api.platform.integration.cohort.persistence.repository

import net.blueshell.api.platform.integration.cohort.persistence.Cohort
import net.blueshell.api.platform.integration.cohort.persistence.CohortKind
import net.blueshell.api.shared.repository.BaseRepository
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

    fun findBySubjectIdAndSystem(subjectId: Long, system: String): Cohort?

    /** Active cohort already owning [externalId] on [system], if any (CohortTargetIds uniqueness guard). */
    fun findFirstBySystemAndExternalId(system: String, externalId: String): Cohort?
}

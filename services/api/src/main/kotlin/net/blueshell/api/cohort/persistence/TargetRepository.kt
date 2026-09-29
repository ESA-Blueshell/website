package net.blueshell.api.cohort.persistence

import net.blueshell.api.shared.repository.BaseRepository
import org.springframework.stereotype.Repository

/**
 * `system` is a plain string holding a `TargetSystem.name()`; the
 * persistence layer cannot depend on the `sync.port` package.
 * Application code resolves the enum.
 */
@Repository
interface TargetRepository : BaseRepository<Target, Long> {
    fun findAllBySystem(system: String): List<Target>

    fun findAllBySystemAndKind(
        system: String,
        kind: TargetKind,
    ): List<Target>

    fun findAllByCohortId(cohortId: Long): List<Target>

    fun findAllByCohortIdIsNotNullAndExternalIdIsNull(): List<Target>

    fun countByCohortId(cohortId: Long): Long

    fun findByCohortIdAndSystem(
        cohortId: Long,
        system: String,
    ): Target?

    /** Active cohort already owning [externalId] on [system], if any (CohortTargetIds uniqueness guard). */
    fun findFirstBySystemAndExternalId(
        system: String,
        externalId: String,
    ): Target?
}

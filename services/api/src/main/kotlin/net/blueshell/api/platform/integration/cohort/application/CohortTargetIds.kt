package net.blueshell.api.platform.integration.cohort.application

import net.blueshell.api.platform.integration.cohort.persistence.Cohort
import net.blueshell.api.platform.integration.cohort.persistence.repository.CohortRepository
import net.blueshell.api.platform.integration.sync.application.ExternalIdMappingService
import net.blueshell.api.platform.integration.sync.application.ExternalIdMappingService.Companion.COHORT_AGGREGATE
import net.blueshell.api.shared.job.NonRetryableJobException
import org.springframework.http.HttpStatus
import org.springframework.stereotype.Component
import org.springframework.transaction.annotation.Transactional
import org.springframework.web.server.ResponseStatusException

/**
 * Sole owner of a cohort's external target id. Reads it from
 * [Cohort.externalId], falling back to the legacy
 * `external_id_mapping(aggregate_type='COHORT')` row for cohorts an older
 * replica wrote during a deploy overlap. Item 8 runs the final backfill and
 * drops the fallback.
 *
 * This is field ownership, not a use case, so it is a plain component rather
 * than an inbound port: every cohort path that needs the target id resolves
 * it here, and [record] is the only writer of the column.
 */
@Component
class CohortTargetIds(
    private val externalIds: ExternalIdMappingService,
    private val cohorts: CohortRepository,
) {
    /**
     * The cohort's target id — the column first, then the legacy mapping.
     * Read-only: never back-fills the column, so read-only callers stay so.
     */
    fun find(cohort: Cohort): String? =
        cohort.externalId?.takeIf { it.isNotBlank() }
            ?: externalIds.find(COHORT_AGGREGATE, cohort.id!!, cohort.system)?.externalId

    /** The target id, or a terminal failure when the cohort is not materialised. */
    fun require(cohort: Cohort): String =
        find(cohort) ?: throw NonRetryableJobException("Cohort ${cohort.id} has no external id on ${cohort.system}")

    /**
     * Records [externalId] as this cohort's target. Rejects blanks and refuses
     * to point a second active cohort at an id already in use. Writes the
     * column and keeps the legacy mapping in step for the compatibility window.
     */
    @Transactional
    fun record(cohort: Cohort, externalId: String): Cohort {
        require(externalId.isNotBlank()) { "Cohort external id must not be blank" }
        val owner = cohorts.findFirstBySystemAndExternalId(cohort.system, externalId)
        if (owner != null && owner.id != cohort.id) {
            throw ResponseStatusException(
                HttpStatus.CONFLICT,
                "${cohort.system} target $externalId is already linked to cohort ${owner.id}",
            )
        }
        cohort.externalId = externalId
        val saved = cohorts.save(cohort)
        externalIds.upsert(COHORT_AGGREGATE, cohort.id!!, cohort.system, externalId)
        return saved
    }
}

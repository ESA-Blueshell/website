package net.blueshell.api.cohort.domain

import net.blueshell.api.cohort.persistence.Cohort
import net.blueshell.api.cohort.persistence.CohortRepository
import net.blueshell.api.shared.job.NonRetryableJobException
import org.springframework.http.HttpStatus
import org.springframework.stereotype.Component
import org.springframework.transaction.annotation.Transactional
import org.springframework.web.server.ResponseStatusException

/**
 * Sole owner of a cohort's external target id, which lives in
 * [Cohort.externalId]. V79 backfilled the column from the legacy
 * `external_id_mapping(aggregate_type='COHORT')` rows, so the read no longer
 * falls back to that mapping (the rows themselves are dropped in a later
 * migration).
 *
 * This is field ownership, not a use case, so it is a plain component rather
 * than an inbound port: every cohort path that needs the target id resolves
 * it here, and [record] is the only writer of the column.
 */
@Component
class CohortTargetIds(
    private val cohorts: CohortRepository,
) {
    /** The cohort's target id, or null when it has not been materialised. */
    fun find(cohort: Cohort): String? = cohort.externalId?.takeIf { it.isNotBlank() }

    /** The target id, or a terminal failure when the cohort is not materialised. */
    fun require(cohort: Cohort): String =
        find(cohort) ?: throw NonRetryableJobException("Cohort ${cohort.id} has no external id on ${cohort.system}")

    /**
     * Records [externalId] as this cohort's target. Rejects blanks and refuses
     * to point a second active cohort at an id already in use.
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
        return cohorts.save(cohort)
    }
}

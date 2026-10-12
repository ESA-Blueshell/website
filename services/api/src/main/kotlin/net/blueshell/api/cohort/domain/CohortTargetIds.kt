package net.blueshell.api.cohort.domain

import net.blueshell.api.cohort.persistence.Target
import net.blueshell.api.cohort.persistence.TargetRepository
import net.blueshell.api.shared.job.NonRetryableJobException
import org.springframework.http.HttpStatus
import org.springframework.stereotype.Component
import org.springframework.transaction.annotation.Transactional
import org.springframework.web.server.ResponseStatusException

/**
 * Sole owner of a cohort's external target id, which lives in [Target.externalId].
 *
 * Every cohort path resolves the target id here, and [record] is the column's only writer.
 */
@Component
class CohortTargetIds(
    private val targets: TargetRepository,
) {
    /** The cohort's target id, or null when it has not been materialised. */
    fun find(target: Target): String? = target.externalId?.takeIf { it.isNotBlank() }

    /** The target id, or a terminal failure when the cohort is not materialised. */
    fun require(target: Target): String =
        find(target) ?: throw NonRetryableJobException("Target ${target.id} has no external id on ${target.system}")

    /**
     * Records [externalId] as this cohort's target. Rejects blanks and refuses
     * to point a second active cohort at an id already in use.
     */
    @Transactional
    fun record(
        target: Target,
        externalId: String,
    ): Target {
        require(externalId.isNotBlank()) { "Cohort external id must not be blank" }
        val owner = targets.findFirstBySystemAndExternalId(target.system, externalId)
        if (owner != null && owner.id != target.id) {
            throw ResponseStatusException(
                HttpStatus.CONFLICT,
                "${target.system} target $externalId is already linked to cohort ${owner.id}",
            )
        }
        target.externalId = externalId
        return targets.save(target)
    }
}

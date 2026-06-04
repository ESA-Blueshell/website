package net.blueshell.api.platform.integration.cohort.application.ledger

import net.blueshell.api.platform.integration.cohort.persistence.Cohort
import net.blueshell.api.platform.integration.cohort.persistence.CohortMember
import net.blueshell.api.platform.integration.cohort.persistence.CohortSubject
import net.blueshell.api.platform.integration.cohort.persistence.repository.CohortMemberRepository
import org.springframework.stereotype.Component
import java.time.LocalDateTime

/**
 * Single writer for `cohort_member` sync state. Every transition of
 * `syncedAt` / `verifiedAt` / stranger rows goes through here, so the
 * per-member sync path and the reconcile verifier share one definition
 * of each state change instead of hand-rolling `row.apply { … }; save`.
 *
 * Callers own their transaction; these methods assume one is active.
 */
@Component
class CohortLedger(private val members: CohortMemberRepository) {

    /**
     * A successful per-member push. Stamps `syncedAt` (and the external
     * id) on the desired row. Returns false if the row is gone (the
     * evaluator removed it mid-flight), so the caller can log the miss.
     */
    fun markPushed(cohortId: Long, userId: Long, externalUserId: String, at: LocalDateTime): Boolean {
        val row = members.findByCohortIdAndUserId(cohortId, userId) ?: return false
        row.externalUserId = externalUserId
        row.syncedAt = at
        members.save(row)
        return true
    }

    /**
     * Reconcile confirmed a desired row present in the live snapshot.
     * Stamps `verifiedAt`, ensures `syncedAt` is set (present implies
     * pushed), and records the external id + label.
     */
    fun markVerified(row: CohortMember, externalUserId: String, label: String?, at: LocalDateTime) {
        row.externalUserId = externalUserId
        if (row.syncedAt == null) row.syncedAt = at
        row.verifiedAt = at
        row.label = label
        members.save(row)
    }

    /**
     * Reconcile found a previously-pushed desired row absent remotely.
     * Clears both stamps so it re-buckets as not-synced; the caller
     * re-enqueues an ADD.
     */
    fun markDrifted(row: CohortMember) {
        row.syncedAt = null
        row.verifiedAt = null
        members.save(row)
    }

    /** Upserts a stranger row (no local user) for a remote id with no desired owner. */
    fun upsertStranger(
        cohort: Cohort,
        subject: CohortSubject,
        externalUserId: String,
        label: String?,
        at: LocalDateTime,
    ) {
        // A blank external id would produce an INVALID stranger row (see
        // CohortMemberState); reject it at the edge so the ledger never holds one.
        require(externalUserId.isNotBlank()) { "Stranger external id must not be blank for cohort ${cohort.id}" }
        val existing = members.findByCohortIdAndExternalUserIdAndUserIdIsNull(cohort.id!!, externalUserId)
        if (existing != null) {
            existing.verifiedAt = at
            existing.label = label
            members.save(existing)
        } else {
            members.save(
                CohortMember(
                    cohort = cohort,
                    userId = null,
                    subject = subject,
                    externalUserId = externalUserId,
                    verifiedAt = at,
                    label = label,
                ),
            )
        }
    }

    /** Soft-deletes the stranger row for an id (looked up; gone remotely or claimed by a user). */
    fun removeStranger(cohortId: Long, externalUserId: String) {
        members.findByCohortIdAndExternalUserIdAndUserIdIsNull(cohortId, externalUserId)
            ?.let { members.delete(it) }
    }

    /** Soft-deletes an already-loaded stranger row. */
    fun removeStranger(stranger: CohortMember) {
        members.delete(stranger)
    }

    /**
     * Claim fold: a linked user already had a stranger row. Move its
     * external state onto the desired row (the member is confirmed
     * present, so it counts as synced + verified) and drop the stranger.
     */
    fun foldStrangerIntoDesired(desired: CohortMember, stranger: CohortMember) {
        desired.externalUserId = stranger.externalUserId
        desired.syncedAt = stranger.verifiedAt
        desired.verifiedAt = stranger.verifiedAt
        desired.label = stranger.label
        members.save(desired)
        members.delete(stranger)
    }
}

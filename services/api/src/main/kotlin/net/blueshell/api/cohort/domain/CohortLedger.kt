package net.blueshell.api.cohort.domain

import net.blueshell.api.cohort.persistence.Cohort
import net.blueshell.api.cohort.persistence.Target
import net.blueshell.api.cohort.persistence.TargetMember
import net.blueshell.api.cohort.persistence.TargetMemberRepository
import net.blueshell.api.shared.job.NonRetryableJobException
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
class CohortLedger(
    private val members: TargetMemberRepository,
) {
    /**
     * A successful per-member push. Stamps `syncedAt` (and the external
     * id) on the desired row. Returns false if the row is gone (the
     * evaluator removed it mid-flight), so the caller can log the miss.
     */
    fun markPushed(
        targetId: Long,
        userId: Long,
        externalUserId: String,
        at: LocalDateTime,
    ): Boolean {
        val row = members.findByTargetIdAndUserId(targetId, userId) ?: return false
        claimExternalIdForDesired(row, externalUserId)
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
    fun markVerified(
        row: TargetMember,
        externalUserId: String,
        label: String?,
        at: LocalDateTime,
    ) {
        claimExternalIdForDesired(row, externalUserId)
        row.externalUserId = externalUserId
        if (row.syncedAt == null) row.syncedAt = at
        row.verifiedAt = at
        row.label = label
        members.save(row)
    }

    /**
     * Batch reconcile confirmation. Matching strangers are claimed and flushed
     * once before any desired row receives an external id, avoiding live-key
     * overlap on `uk_cohort_member_external`.
     */
    fun markVerified(
        confirmations: Collection<DesiredConfirmation>,
        at: LocalDateTime,
    ): Set<String> {
        val safeConfirmations =
            confirmations
                .groupBy { it.externalUserId }
                .filterValues { it.size == 1 }
                .values
                .flatten()
        safeConfirmations
            .groupBy { it.row.target.id!! }
            .forEach { (targetId, rows) ->
                rows.forEach { guardDesiredExternalOwner(it.row, it.externalUserId) }
                claimMatchingStrangers(targetId, rows.map { it.externalUserId }.toSet())
            }
        safeConfirmations.forEach { confirmation ->
            confirmation.row.externalUserId = confirmation.externalUserId
            if (confirmation.row.syncedAt == null) confirmation.row.syncedAt = at
            confirmation.row.verifiedAt = at
            confirmation.row.label = confirmation.label
        }
        members.saveAll(safeConfirmations.map { it.row })
        return safeConfirmations.map { it.externalUserId }.toSet()
    }

    /**
     * Reconcile found a previously-pushed desired row absent remotely.
     * Clears both stamps so it re-buckets as not-synced; the caller
     * re-enqueues an ADD.
     */
    fun markDrifted(row: TargetMember) {
        row.syncedAt = null
        row.verifiedAt = null
        members.save(row)
    }

    /** Upserts a stranger row (no local user) for a remote id with no desired owner. */
    fun upsertStranger(
        target: Target,
        cohort: Cohort,
        externalUserId: String,
        label: String?,
        at: LocalDateTime,
    ) {
        // A blank external id would produce an INVALID stranger row (see
        // TargetMemberState); reject it at the edge so the ledger never holds one.
        require(externalUserId.isNotBlank()) { "Stranger external id must not be blank for cohort ${target.id}" }
        val existing = members.findByTargetIdAndExternalUserIdAndUserIdIsNull(target.id!!, externalUserId)
        if (existing != null) {
            existing.verifiedAt = at
            existing.label = label
            members.save(existing)
        } else if (members.findByTargetIdAndExternalUserIdAndUserIdIsNotNull(target.id!!, externalUserId) != null) {
            return
        } else {
            members.save(
                TargetMember(
                    target = target,
                    userId = null,
                    cohort = cohort,
                    externalUserId = externalUserId,
                    verifiedAt = at,
                    label = label,
                ),
            )
        }
    }

    /** Soft-deletes the stranger row for an id (looked up; gone remotely or claimed by a user). */
    fun removeStranger(
        targetId: Long,
        externalUserId: String,
    ) {
        members
            .findByTargetIdAndExternalUserIdAndUserIdIsNull(targetId, externalUserId)
            ?.let { members.delete(it) }
    }

    /** Soft-deletes an already-loaded stranger row. */
    fun removeStranger(stranger: TargetMember) {
        members.delete(stranger)
    }

    /**
     * Claim fold: a linked user already had a stranger row. Move its
     * external state onto the desired row (the member is confirmed
     * present, so it counts as synced + verified) and drop the stranger.
     */
    fun foldStrangerIntoDesired(
        desired: TargetMember,
        stranger: TargetMember,
    ) {
        val externalUserId = stranger.externalUserId
        val verifiedAt = stranger.verifiedAt
        val label = stranger.label
        guardDesiredExternalOwner(desired, externalUserId)
        members.delete(stranger)
        members.flush()
        desired.externalUserId = externalUserId
        desired.syncedAt = verifiedAt
        desired.verifiedAt = verifiedAt
        desired.label = label
        members.save(desired)
    }

    private fun claimExternalIdForDesired(
        row: TargetMember,
        externalUserId: String,
    ) {
        guardDesiredExternalOwner(row, externalUserId)
        claimMatchingStrangers(row.target.id!!, setOf(externalUserId))
    }

    private fun guardDesiredExternalOwner(
        row: TargetMember,
        externalUserId: String?,
    ) {
        if (externalUserId.isNullOrBlank()) return
        val targetId = row.target.id!!
        val owner = members.findByTargetIdAndExternalUserIdAndUserIdIsNotNull(targetId, externalUserId) ?: return
        if (owner.userId == row.userId || (owner.id != null && owner.id == row.id)) return
        throw ExternalIdAlreadyOwnedException(targetId, externalUserId, owner.userId, row.userId)
    }

    private fun claimMatchingStrangers(
        targetId: Long,
        externalUserIds: Set<String>,
    ) {
        if (externalUserIds.isEmpty()) return
        val strangers = members.findAllByTargetIdAndExternalUserIdInAndUserIdIsNull(targetId, externalUserIds)
        if (strangers.isEmpty()) return
        strangers.forEach { members.delete(it) }
        members.flush()
    }

    data class DesiredConfirmation(
        val row: TargetMember,
        val externalUserId: String,
        val label: String?,
    )
}

class ExternalIdAlreadyOwnedException(
    targetId: Long,
    externalUserId: String,
    ownerUserId: Long?,
    requestedUserId: Long?,
) : NonRetryableJobException(
        "Cannot assign external user id '$externalUserId' in cohort $targetId to user $requestedUserId; " +
            "it is already owned by user $ownerUserId in the same cohort.",
    )

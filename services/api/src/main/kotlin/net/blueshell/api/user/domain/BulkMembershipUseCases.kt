package net.blueshell.api.user.domain

import net.blueshell.api.shared.dto.bulk.BulkActionResult
import net.blueshell.api.shared.dto.bulk.BulkRowDisposition
import net.blueshell.api.shared.dto.bulk.BulkRowReason
import net.blueshell.api.shared.dto.bulk.BulkSelectionRejected
import net.blueshell.api.user.api.MembershipService
import net.blueshell.api.user.api.UserErasureService
import net.blueshell.api.user.api.UserService
import net.blueshell.api.user.persistence.Membership
import org.springframework.stereotype.Service
import org.springframework.transaction.annotation.Transactional
import java.time.LocalDate

/**
 * Ends the memberships of a whole selection at once.
 *
 * Preview and execute are the same reading of the same rows: both call [decide], both pin
 * one [LocalDate] for the request, so what the dialog showed is what the api does. The
 * writes themselves go through [MembershipUseCases], which is the only place the interval
 * invariants are stated — a bulk end cannot leave behind a membership a single end would
 * have been refused for.
 *
 * A selection naming users the action cannot read is refused whole, matching the
 * contribution bulk actions: the operator chose a set, and acting on part of it leaves
 * them unable to tell which rows moved.
 */
@Service
class BulkMembershipUseCases(
    private val memberships: MembershipService,
    private val users: UserService,
    private val erasure: UserErasureService,
    private val membershipUseCases: MembershipUseCases,
) {
    @Transactional(readOnly = true)
    fun preview(userIds: List<Long>, operation: BulkMembershipOperation): BulkMembershipPreview {
        val today = LocalDate.now()
        val decisions = decideAll(userIds, operation, today)
        return BulkMembershipPreview(
            effectiveDate = today,
            rows = decisions.map { (userId, decision) ->
                BulkMembershipPreviewRow(userId = userId, disposition = decision.disposition, reason = decision.reason)
            },
        )
    }

    @Transactional
    fun execute(userIds: List<Long>, operation: BulkMembershipOperation): BulkActionResult {
        val today = LocalDate.now()
        val decisions = decideAll(userIds, operation, today)

        var applied = 0
        var skipped = 0
        for ((_, decision) in decisions) {
            if (decision.disposition != BulkRowDisposition.INCLUDED) {
                skipped++
                continue
            }
            when (operation) {
                BulkMembershipOperation.END ->
                    decision.affected.forEach { membershipUseCases.end(it, today) }
            }
            applied++
        }
        return BulkActionResult(applied = applied, skipped = skipped)
    }

    /**
     * One decision per selected user, in the order they were selected, against a single
     * [today]. Reading every membership up front is also what stops the two endpoints
     * disagreeing: neither re-reads a row after deciding about it.
     */
    private fun decideAll(
        userIds: List<Long>,
        operation: BulkMembershipOperation,
        today: LocalDate,
    ): List<Pair<Long, Decision>> {
        val distinct = userIds.distinct()
        rejectUnreadable(distinct, operation)
        return distinct.map { userId ->
            userId to decide(operation, memberships.findByUserId(userId), today)
        }
    }

    /**
     * What the action does to one member, from their whole set of memberships.
     *
     * The only reading of the rules there is: the preview renders it and the execute acts
     * on it, so a row cannot be shown as skipped and then quietly changed.
     */
    private fun decide(operation: BulkMembershipOperation, held: List<Membership>, today: LocalDate): Decision =
        when (operation) {
            BulkMembershipOperation.END -> decideEnd(held, today)
        }

    private fun decideEnd(held: List<Membership>, today: LocalDate): Decision {
        val active = held.filter { it.endDate == null }
        if (active.isEmpty()) return Decision(BulkRowDisposition.SKIPPED, BulkRowReason.NO_ACTIVE_MEMBERSHIP)

        // A membership that started today has no day to span, and ending it would be
        // refused as a zero-day interval. Named rather than dropped, so the operator sees
        // why somebody they selected stayed a member.
        val endable = active.filter { it.startDate.isBefore(today) }
        if (endable.isEmpty()) return Decision(BulkRowDisposition.SKIPPED, BulkRowReason.STARTED_TODAY)

        return Decision(BulkRowDisposition.INCLUDED, reason = null, affected = endable.mapNotNull { it.id })
    }

    /**
     * Refuses a selection naming ids the action cannot read. A deleted user still resolves
     * by id — the erasure snapshot is what tells them apart — so both are checked.
     */
    private fun rejectUnreadable(userIds: List<Long>, operation: BulkMembershipOperation) {
        val unknown = userIds.filterNot { users.existsById(it) }
        val deleted = userIds.filterNot { it in unknown }.filter { erasure.isDeleted(it) }

        val violations = buildList {
            if (unknown.isNotEmpty()) {
                add(
                    BulkSelectionRejected.Violation(
                        field = "userIds",
                        code = BulkSelectionRejected.UNKNOWN_USERS,
                        values = unknown,
                        message = "${unknown.size} of the selected users no longer exist.",
                    ),
                )
            }
            if (deleted.isNotEmpty()) {
                add(
                    BulkSelectionRejected.Violation(
                        field = "userIds",
                        code = BulkSelectionRejected.DELETED_USERS,
                        values = deleted,
                        message = "${deleted.size} of the selected users have been deleted.",
                    ),
                )
            }
        }
        if (violations.isNotEmpty()) throw BulkSelectionRejected(requestNameOf(operation), violations)
    }

    private fun requestNameOf(operation: BulkMembershipOperation): String = when (operation) {
        BulkMembershipOperation.END -> "BulkEndMembershipRequest"
    }

    /** A row's disposition together with the memberships the execute will write to. */
    private data class Decision(
        val disposition: BulkRowDisposition,
        val reason: BulkRowReason?,
        val affected: List<Long> = emptyList(),
    )
}

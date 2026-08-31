package net.blueshell.api.user.domain

import net.blueshell.api.shared.dto.bulk.BulkActionResult
import net.blueshell.api.shared.dto.bulk.BulkRowDisposition
import net.blueshell.api.shared.dto.bulk.BulkRowReason
import net.blueshell.api.shared.dto.bulk.BulkSelectionRejected
import net.blueshell.api.shared.enums.MemberType
import net.blueshell.api.user.api.MembershipService
import net.blueshell.api.user.api.UserErasureService
import net.blueshell.api.user.api.UserService
import net.blueshell.api.user.persistence.Membership
import org.springframework.stereotype.Service
import org.springframework.transaction.annotation.Transactional
import java.time.LocalDate

/**
 * Ends and starts the memberships of a whole selection at once.
 *
 * Preview and execute are the same reading of the same rows: both call [decide], both pin
 * one [LocalDate] for the request, so what the dialog showed is what the api does. The
 * writes themselves go through [MembershipUseCases], which is the only place the interval
 * invariants are stated — a bulk change cannot leave behind a membership a single edit
 * would have been refused for.
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
        return BulkMembershipPreview(
            effectiveDate = today,
            rows = decideAll(userIds, operation, today).map { (userId, decision) ->
                BulkMembershipPreviewRow(userId = userId, disposition = decision.disposition, reason = decision.reason)
            },
        )
    }

    @Transactional
    fun execute(userIds: List<Long>, operation: BulkMembershipOperation): BulkActionResult {
        val today = LocalDate.now()

        var applied = 0
        var skipped = 0
        for ((userId, decision) in decideAll(userIds, operation, today)) {
            when (decision) {
                is Decision.Skip -> skipped++
                is Decision.End -> {
                    decision.membershipIds.forEach { membershipUseCases.end(it, today) }
                    applied++
                }
                is Decision.Start -> {
                    membershipUseCases.boardCreate(
                        userId = userId,
                        memberType = decision.memberType,
                        startDate = today,
                        endDate = null,
                        incasso = decision.incasso,
                    )
                    applied++
                }
            }
        }
        return BulkActionResult(applied = applied, skipped = skipped)
    }

    /**
     * One decision per selected user, in the order they were selected, against a single
     * [today]. Every membership is read in one query up front, which is also what stops the
     * two endpoints disagreeing: neither re-reads a row after deciding about it.
     */
    private fun decideAll(
        userIds: List<Long>,
        operation: BulkMembershipOperation,
        today: LocalDate,
    ): List<Pair<Long, Decision>> {
        val distinct = userIds.distinct()
        rejectUnreadable(distinct, operation)
        val held = memberships.findByUserIds(distinct)
        return distinct.map { userId ->
            userId to decide(operation, held[userId] ?: emptyList(), today)
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
            BulkMembershipOperation.START -> decideStart(held)
        }

    private fun decideEnd(held: List<Membership>, today: LocalDate): Decision {
        val active = held.filter { it.endDate == null }
        if (active.isEmpty()) return Decision.Skip(BulkRowReason.NO_ACTIVE_MEMBERSHIP)

        // A membership that started today has no day to span, and ending it would be
        // refused as a zero-day interval. Named rather than dropped, so the operator sees
        // why somebody they selected stayed a member.
        val endable = active.filter { it.startDate.isBefore(today) }
        if (endable.isEmpty()) return Decision.Skip(BulkRowReason.STARTED_TODAY)

        return Decision.End(endable.mapNotNull { it.id })
    }

    /**
     * A returning member gets a fresh spell rather than their old one reopened, so the
     * history reads as two stays rather than one long one — and "member since", which is
     * the earliest start across the set, still shows the day they first joined.
     *
     * The type and the incasso mandate carry over from the most recent spell, because a
     * returning alumnus is still an alumnus and their payment arrangement has not changed.
     */
    private fun decideStart(held: List<Membership>): Decision {
        if (held.any { it.endDate == null }) return Decision.Skip(BulkRowReason.ALREADY_ACTIVE)

        val previous = held.maxByOrNull { it.startDate }
        return Decision.Start(
            memberType = previous?.memberType ?: MemberType.REGULAR,
            incasso = previous?.incasso ?: false,
        )
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
        BulkMembershipOperation.START -> "BulkStartMembershipRequest"
    }

    /** What one row will have done to it, and what the execute needs to do it. */
    private sealed interface Decision {
        val disposition: BulkRowDisposition
        val reason: BulkRowReason?

        /** Nothing to do, and why. */
        data class Skip(override val reason: BulkRowReason) : Decision {
            override val disposition = BulkRowDisposition.SKIPPED
        }

        /** These memberships will be closed. */
        data class End(val membershipIds: List<Long>) : Decision {
            override val disposition = BulkRowDisposition.INCLUDED
            override val reason: BulkRowReason? = null
        }

        /** A membership will be opened today, carrying these terms over from the last one. */
        data class Start(val memberType: MemberType, val incasso: Boolean) : Decision {
            override val disposition = BulkRowDisposition.INCLUDED
            override val reason = BulkRowReason.WILL_START_NEW
        }
    }
}

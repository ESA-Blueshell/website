package net.blueshell.api.user.domain

import net.blueshell.api.shared.dto.bulk.BulkRowDisposition
import net.blueshell.api.shared.dto.bulk.BulkRowReason
import net.blueshell.api.shared.enums.MemberType
import net.blueshell.api.user.persistence.Membership
import java.time.LocalDate

/**
 * What a bulk membership action will do to one member, and what the writing needs to do it.
 *
 * The disposition and the reason are what the preview shows; the rest is what the apply
 * acts on. Both come out of the same [BulkMembershipDecisions.decide] call, which is why a
 * row cannot be shown as skipped and then quietly changed.
 */
sealed interface BulkMembershipDecision {
    val disposition: BulkRowDisposition
    val reason: BulkRowReason?

    /** Nothing to do, and why. */
    data class Skip(override val reason: BulkRowReason) : BulkMembershipDecision {
        override val disposition = BulkRowDisposition.SKIPPED
    }

    /** These memberships will be closed. */
    data class End(val membershipIds: List<Long>) : BulkMembershipDecision {
        override val disposition = BulkRowDisposition.INCLUDED
        override val reason: BulkRowReason? = null
    }

    /** A membership of this type will be opened today, with no incasso mandate. */
    data class Start(val memberType: MemberType) : BulkMembershipDecision {
        override val disposition = BulkRowDisposition.INCLUDED
        override val reason = BulkRowReason.WILL_START_NEW
    }
}

/**
 * The rules the bulk membership actions run on, over one member's whole set of memberships
 * and the single date the action is pinned to.
 *
 * Pure, and kept apart from [BulkMembershipUseCases], which reads the rows and does the
 * writing. Separating them is what makes the rules something a test can state directly
 * rather than infer from what a batch did.
 */
object BulkMembershipDecisions {

    fun decide(
        operation: BulkMembershipOperation,
        held: List<Membership>,
        today: LocalDate,
    ): BulkMembershipDecision = when (operation) {
        BulkMembershipOperation.END -> decideEnd(held, today)
        BulkMembershipOperation.START -> decideStart(held)
    }

    private fun decideEnd(held: List<Membership>, today: LocalDate): BulkMembershipDecision {
        val active = held.filter { it.endDate == null }
        if (active.isEmpty()) return BulkMembershipDecision.Skip(BulkRowReason.NO_ACTIVE_MEMBERSHIP)

        // A membership that started today has no day to span, and ending it would be
        // refused as a zero-day interval. Named rather than dropped, so the operator sees
        // why somebody they selected stayed a member.
        val endable = active.filter { it.startDate.isBefore(today) }
        if (endable.isEmpty()) return BulkMembershipDecision.Skip(BulkRowReason.STARTED_TODAY)

        return BulkMembershipDecision.End(
            endable.map { requireNotNull(it.id) { "a membership read from the database has an id" } },
        )
    }

    /**
     * A returning member gets a fresh spell rather than their old one reopened, so the history
     * reads as two stays while "member since" still shows the day they first joined.
     *
     * The member type carries over — a returning alumnus is still an alumnus, and the fee is
     * read off it. The incasso mandate does not: a standing authorisation to take money, given
     * years ago for a membership that ended, is not one to re-arm in a batch.
     */
    private fun decideStart(held: List<Membership>): BulkMembershipDecision {
        if (held.any { it.endDate == null }) return BulkMembershipDecision.Skip(BulkRowReason.ALREADY_ACTIVE)

        // No two spells can share a start date — they would overlap, which the interval
        // invariants refuse — so the most recent one is unambiguous.
        val previous = held.maxByOrNull { it.startDate }
        return BulkMembershipDecision.Start(memberType = previous?.memberType ?: MemberType.REGULAR)
    }
}

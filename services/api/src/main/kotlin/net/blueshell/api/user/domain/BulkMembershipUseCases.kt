package net.blueshell.api.user.domain

import net.blueshell.api.shared.dto.bulk.BulkActionResult
import net.blueshell.api.shared.dto.bulk.BulkSelectionRejected
import net.blueshell.api.shared.dto.bulk.BulkUserSelection
import net.blueshell.api.user.api.MembershipService
import net.blueshell.api.user.api.UserErasureService
import net.blueshell.api.user.api.UserService
import org.springframework.stereotype.Service
import org.springframework.transaction.annotation.Transactional
import java.time.LocalDate

/**
 * Ends and starts the memberships of a whole selection at once.
 *
 * Preview and apply read the same rows through [BulkMembershipDecisions] against one pinned
 * date, so what the dialog showed is what the api does. Writes go through [MembershipUseCases],
 * the only place the interval invariants are stated, so a bulk change cannot leave a membership
 * a single edit would have been refused for. A selection naming users the action cannot read is
 * refused whole, since acting on part of a set leaves the operator unable to tell what moved.
 */
@Service
class BulkMembershipUseCases(
    private val memberships: MembershipService,
    private val users: UserService,
    private val erasure: UserErasureService,
    private val membershipUseCases: MembershipUseCases,
) {
    @Transactional(readOnly = true)
    fun preview(userIds: List<Long>, operation: BulkMembershipOperation): BulkMembershipPlan =
        plan(userIds, operation)

    @Transactional
    fun execute(userIds: List<Long>, operation: BulkMembershipOperation): BulkActionResult {
        val plan = plan(userIds, operation)

        var applied = 0
        var skipped = 0
        for ((userId, decision) in plan.rows.map { it.userId to it.decision }) {
            when (decision) {
                is BulkMembershipDecision.Skip -> skipped++
                is BulkMembershipDecision.End -> {
                    decision.membershipIds.forEach { membershipUseCases.end(it, plan.effectiveDate) }
                    applied++
                }
                is BulkMembershipDecision.Start -> {
                    membershipUseCases.boardCreate(
                        userId = userId,
                        memberType = decision.memberType,
                        startDate = plan.effectiveDate,
                        endDate = null,
                        incasso = false,
                    )
                    applied++
                }
            }
        }
        return BulkActionResult(applied = applied, skipped = skipped)
    }

    /**
     * One decision per selected user, in the order they were selected, against a single
     * date. Every membership is read in one query up front, which is also what stops the
     * two endpoints disagreeing: neither re-reads a row after deciding about it.
     */
    private fun plan(userIds: List<Long>, operation: BulkMembershipOperation): BulkMembershipPlan {
        val distinct = userIds.distinct()
        val classified = BulkUserSelection.classify(distinct, users::existsById, erasure::isDeleted)
        if (classified.violations.isNotEmpty()) {
            throw BulkSelectionRejected(REQUEST_NAME, classified.violations)
        }

        val today = LocalDate.now()
        val held = memberships.findByUserIds(distinct)
        return BulkMembershipPlan(
            effectiveDate = today,
            rows = distinct.map { userId ->
                BulkMembershipPlan.Row(userId, BulkMembershipDecisions.decide(operation, held[userId] ?: emptyList(), today))
            },
        )
    }

    private companion object {
        /** Names the field a refusal attaches to; both actions take the same request shape. */
        const val REQUEST_NAME = "BulkMembershipRequest"
    }
}

package net.blueshell.api.contribution.domain

import net.blueshell.api.contribution.persistence.Contribution
import net.blueshell.api.user.api.MembershipService
import net.blueshell.api.user.api.UserService
import net.blueshell.api.user.api.UserErasureService
import net.blueshell.api.shared.dto.bulk.BulkActionResult
import net.blueshell.api.shared.dto.bulk.BulkSelectionRejected
import net.blueshell.api.shared.dto.bulk.BulkUserSelection
import net.blueshell.api.shared.enums.MemberType
import org.springframework.stereotype.Service
import org.springframework.transaction.annotation.Transactional
import net.blueshell.api.contribution.api.ContributionPeriodService
import net.blueshell.api.contribution.api.ContributionService

/**
 * Records or removes contributions for a set of users in one period.
 *
 * The whole selection is checked before anything is written, and one naming a user the action
 * cannot touch is refused with those ids rather than applied to the remainder: acting on part
 * of a set leaves the operator unable to tell which rows moved. Writing is idempotent, so a
 * repeated request settles at the same state, and a row already in the
 * requested state is reported unchanged, which is a fact about the data, not a rejected input.
 */
@Service
class BulkContributionUseCases(
    private val contributions: ContributionService,
    private val users: UserService,
    private val memberships: MembershipService,
    private val periods: ContributionPeriodService,
    private val erasure: UserErasureService,
) {
    @Transactional
    fun execute(userIds: List<Long>, contributionPeriodId: Long, operation: BulkContributionOperation): BulkActionResult {

        val userIds = userIds.distinct()
        val periodId = contributionPeriodId
        val objectName = if (operation == BulkContributionOperation.PAID) {
            "BulkMarkPaidRequest"
        } else {
            "BulkMarkUnpaidRequest"
        }

        if (!periods.existsById(periodId)) {
            throw BulkSelectionRejected(
                objectName,
                listOf(
                    BulkSelectionRejected.Violation(
                        field = "contributionPeriodId",
                        code = BulkSelectionRejected.UNKNOWN_PERIOD,
                        values = listOf(periodId),
                        message = "That contribution period no longer exists.",
                    ),
                ),
            )
        }

        // Which ids resolve to a live user is the same question every bulk action asks, so
        // it is asked in one place; only the honorary rule below is this action's own.
        val selection = BulkUserSelection.classify(userIds, users::existsById, erasure::isDeleted)
        // Only actionable ids are inspected; the others have no membership worth reading.
        val honorary = selection.usable.filter { userId ->
            memberships.findByUserId(userId).maxByOrNull { it.startDate }?.memberType == MemberType.HONORARY
        }

        val violations = buildList {
            addAll(selection.violations)
            if (honorary.isNotEmpty()) {
                add(
                    BulkSelectionRejected.Violation(
                        field = "userIds",
                        code = BulkSelectionRejected.HONORARY_USERS,
                        values = honorary,
                        message = "${honorary.size} of the selected users are honorary members and owe no contribution.",
                    ),
                )
            }
        }
        if (violations.isNotEmpty()) throw BulkSelectionRejected(objectName, violations)

        val period = periods.findById(periodId)
        val wantPaid = operation == BulkContributionOperation.PAID
        var applied = 0
        var unchanged = 0

        for (userId in userIds) {
            val recorded = contributions.existsByUserIdAndPeriodId(userId, periodId)
            when {
                wantPaid && !recorded -> {
                    contributions.create(Contribution(user = users.findById(userId), contributionPeriod = period))
                    applied++
                }
                !wantPaid && recorded -> {
                    contributions.deleteById(Contribution.Id(userId, periodId))
                    applied++
                }
                else -> unchanged++
            }
        }

        return BulkActionResult(applied = applied, skipped = unchanged, queued = 0)
    }
}

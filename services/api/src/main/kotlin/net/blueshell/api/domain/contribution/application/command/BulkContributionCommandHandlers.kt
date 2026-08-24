package net.blueshell.api.domain.contribution.application.command

import net.blueshell.api.domain.contribution.application.ContributionPeriodService
import net.blueshell.api.domain.contribution.application.ContributionService
import net.blueshell.api.domain.contribution.command.BulkContributionOperation
import net.blueshell.api.domain.contribution.command.ExecuteBulkContributionCommand
import net.blueshell.api.domain.contribution.persistence.Contribution
import net.blueshell.api.domain.user.application.MembershipService
import net.blueshell.api.domain.user.persistence.repository.DeletedUserRepository
import net.blueshell.api.domain.user.application.UserService
import net.blueshell.api.shared.command.CommandHandler
import net.blueshell.api.shared.dto.bulk.BulkActionResult
import net.blueshell.api.shared.dto.bulk.BulkSelectionRejected
import net.blueshell.api.shared.enums.MemberType
import org.springframework.stereotype.Component
import org.springframework.transaction.annotation.Transactional

/**
 * Records or removes contributions for a set of users in one period.
 *
 * The whole selection is checked before anything is written, and a selection naming a
 * user the action cannot touch is refused with those ids rather than applied to the
 * remainder. A bulk action is a statement about a set; acting on part of it and
 * reporting a count leaves the operator unable to tell which rows moved.
 *
 * Writing is idempotent, so re-sending a request settles at the same state: a
 * contribution is created only where none exists and deleted only where one does.
 * Rows already in the requested state are reported as unchanged, which is a fact
 * about the data rather than a rejected input.
 */
@Component
class ExecuteBulkContributionHandler(
    private val contributions: ContributionService,
    private val users: UserService,
    private val memberships: MembershipService,
    private val periods: ContributionPeriodService,
    private val deletedUsers: DeletedUserRepository,
) : CommandHandler<ExecuteBulkContributionCommand, BulkActionResult> {
    override val commandType = ExecuteBulkContributionCommand::class

    @Transactional
    override fun handle(command: ExecuteBulkContributionCommand): BulkActionResult {
        val userIds = command.userIds.distinct()
        val periodId = command.contributionPeriodId
        val objectName = if (command.operation == BulkContributionOperation.PAID) {
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

        val unknown = userIds.filterNot { users.existsById(it) }
        // Deletion anonymises the account and keeps the row for a restore window, so a
        // deleted user still resolves by id. The snapshot is what distinguishes them.
        val deleted = userIds.filterNot { it in unknown }.filter { deletedUsers.existsById(it) }
        // Only actionable ids are inspected; the others have no membership worth reading.
        val honorary = userIds.filterNot { it in unknown || it in deleted }.filter { userId ->
            memberships.findByUserId(userId).maxByOrNull { it.startDate }?.memberType == MemberType.HONORARY
        }

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
        val wantPaid = command.operation == BulkContributionOperation.PAID
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

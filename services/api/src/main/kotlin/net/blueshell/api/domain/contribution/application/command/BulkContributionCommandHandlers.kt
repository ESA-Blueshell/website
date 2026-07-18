package net.blueshell.api.domain.contribution.application.command

import net.blueshell.api.domain.contribution.application.ContributionPeriodService
import net.blueshell.api.domain.contribution.application.ContributionService
import net.blueshell.api.domain.contribution.command.BulkContributionOperation
import net.blueshell.api.domain.contribution.command.ExecuteBulkContributionCommand
import net.blueshell.api.domain.contribution.persistence.Contribution
import net.blueshell.api.domain.user.application.UserService
import net.blueshell.api.shared.command.CommandHandler
import net.blueshell.api.shared.dto.bulk.BulkActionResult
import org.springframework.stereotype.Component
import org.springframework.transaction.annotation.Transactional

/**
 * Mark-paid / mark-unpaid are now execute-only: their entire decision input
 * (`userId ∈ paidUserIds`?) already lives in the frontend, so the preview is
 * computed client-side and there is no server preview endpoint. Execute stays
 * idempotent — it creates a contribution only if one does not exist (paid) or
 * deletes only if one does (unpaid), so re-running is a no-op for settled rows.
 * See docs/proposals/bulk-actions/REDESIGN.md §1 (preview tiering) & §2.
 */
@Component
class ExecuteBulkContributionHandler(
    private val service: ContributionService,
    private val users: UserService,
    private val periods: ContributionPeriodService,
) : CommandHandler<ExecuteBulkContributionCommand, BulkActionResult> {
    override val commandType = ExecuteBulkContributionCommand::class

    @Transactional
    override fun handle(command: ExecuteBulkContributionCommand): BulkActionResult {
        val periodId = command.contributionPeriodId!!
        val period = periods.findById(periodId)
        val paid = command.operation == BulkContributionOperation.PAID
        var applied = 0
        var skipped = 0
        command.userIds.distinct().forEach { userId ->
            val exists = service.existsByUserIdAndPeriodId(userId, periodId)
            when {
                paid && !exists -> {
                    service.create(Contribution(user = users.findById(userId), contributionPeriod = period))
                    applied++
                }
                !paid && exists -> {
                    service.deleteById(Contribution.Id(userId, periodId))
                    applied++
                }
                else -> skipped++
            }
        }
        return BulkActionResult(applied = applied, skipped = skipped, queued = 0)
    }
}

package net.blueshell.api.domain.contribution.application.command

import net.blueshell.api.domain.contribution.application.ContributionPeriodService
import net.blueshell.api.domain.contribution.application.ContributionService
import net.blueshell.api.domain.contribution.command.BulkContributionOperation
import net.blueshell.api.domain.contribution.command.ExecuteBulkContributionCommand
import net.blueshell.api.domain.contribution.command.PreviewBulkContributionCommand
import net.blueshell.api.domain.contribution.persistence.Contribution
import net.blueshell.api.domain.user.application.UserService
import net.blueshell.api.shared.command.CommandHandler
import net.blueshell.api.shared.dto.bulk.BulkActionResult
import net.blueshell.api.shared.dto.bulk.BulkActionType
import net.blueshell.api.shared.dto.bulk.BulkPreviewResult
import net.blueshell.api.shared.dto.bulk.BulkPreviewRow
import net.blueshell.api.shared.dto.bulk.BulkRowDisposition
import net.blueshell.api.shared.dto.bulk.BulkRowReason
import org.springframework.stereotype.Component
import org.springframework.transaction.annotation.Transactional

@Component
class PreviewBulkContributionHandler(
    private val service: ContributionService,
    private val users: UserService,
    private val periods: ContributionPeriodService,
) : CommandHandler<PreviewBulkContributionCommand, BulkPreviewResult> {
    override val commandType = PreviewBulkContributionCommand::class

    @Transactional(readOnly = true)
    override fun handle(command: PreviewBulkContributionCommand): BulkPreviewResult {
        val periodId = command.contributionPeriodId!!
        periods.findById(periodId) // 404 if the period is unknown
        val paid = command.operation == BulkContributionOperation.PAID
        val rows = command.userIds.distinct().map { userId ->
            val user = users.findById(userId)
            val exists = service.existsByUserIdAndPeriodId(userId, periodId)
            val willApply = if (paid) !exists else exists
            BulkPreviewRow(
                userId = userId,
                name = user.fullName,
                disposition = if (willApply) BulkRowDisposition.INCLUDED else BulkRowDisposition.SKIPPED,
                reason = when {
                    willApply -> null
                    paid -> BulkRowReason.ALREADY_PAID
                    else -> BulkRowReason.NOT_PAID
                },
            )
        }
        return BulkPreviewResult.of(if (paid) BulkActionType.MARK_PAID else BulkActionType.MARK_UNPAID, periodId, rows)
    }
}

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

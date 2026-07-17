package net.blueshell.api.domain.contribution.application.command

import net.blueshell.api.domain.contribution.application.ContributionPeriodService
import net.blueshell.api.domain.contribution.application.ContributionReminderService
import net.blueshell.api.domain.contribution.application.ContributionService
import net.blueshell.api.domain.contribution.command.ExecuteBulkContributionReminderCommand
import net.blueshell.api.domain.contribution.command.PreviewBulkContributionReminderCommand
import net.blueshell.api.domain.contribution.domain.resolveFeeAmount
import net.blueshell.api.domain.contribution.domain.resolveFeeType
import net.blueshell.api.domain.contribution.persistence.ContributionReminder
import net.blueshell.api.domain.user.application.MembershipService
import net.blueshell.api.domain.user.application.UserService
import net.blueshell.api.shared.command.CommandHandler
import net.blueshell.api.shared.dto.bulk.BulkActionResult
import net.blueshell.api.shared.dto.bulk.BulkActionType
import net.blueshell.api.shared.dto.bulk.BulkPreviewResult
import net.blueshell.api.shared.dto.bulk.BulkPreviewRow
import net.blueshell.api.shared.dto.bulk.BulkRowDisposition
import net.blueshell.api.shared.dto.bulk.BulkRowReason
import net.blueshell.api.shared.enums.MemberType
import org.springframework.stereotype.Component
import org.springframework.transaction.annotation.Transactional

@Component
class PreviewBulkContributionReminderHandler(
    private val users: UserService,
    private val memberships: MembershipService,
    private val periods: ContributionPeriodService,
    private val contributions: ContributionService,
    private val reminders: ContributionReminderService,
) : CommandHandler<PreviewBulkContributionReminderCommand, BulkPreviewResult> {
    override val commandType = PreviewBulkContributionReminderCommand::class

    @Transactional(readOnly = true)
    override fun handle(command: PreviewBulkContributionReminderCommand): BulkPreviewResult {
        val periodId = command.contributionPeriodId!!
        val period = periods.findById(periodId)
        val cutoffDate = command.cutoffDate!!

        val rows = command.userIds.distinct().map { userId ->
            val user = users.findById(userId)

            // Get the current (active) membership; use the most recent by start date
            val activeMemberships = memberships.findByUserId(userId)
            val activeMembership = activeMemberships.maxByOrNull { it.startDate }

            // Determine recommended fee type and resolve the € amount
            val memberType = activeMembership?.memberType ?: MemberType.REGULAR
            val membershipStart = activeMembership?.startDate
            val recommendedFeeType = resolveFeeType(memberType, membershipStart, cutoffDate)

            // Check if already paid
            val alreadyPaid = contributions.existsByUserIdAndPeriodId(userId, periodId)

            // Get last sent date from audit
            val lastSent = reminders.findLastReminderForUserAndPeriod(userId, periodId)?.createdAt
                ?.atZone(java.time.ZoneOffset.UTC)?.toLocalDate()

            val disposition: BulkRowDisposition
            val reason: BulkRowReason?

            when {
                recommendedFeeType == null -> {
                    // Honorary member: excluded and not sendable
                    disposition = BulkRowDisposition.EXCLUDED
                    reason = BulkRowReason.HONORARY
                }
                alreadyPaid -> {
                    // Already paid: excluded by default, but may be re-included
                    disposition = BulkRowDisposition.WARNING
                    reason = BulkRowReason.ALREADY_PAID
                }
                else -> {
                    // Include
                    disposition = BulkRowDisposition.INCLUDED
                    reason = null
                }
            }

            BulkPreviewRow(
                userId = userId,
                name = user.fullName,
                memberType = memberType,
                memberSince = activeMembership?.startDate,
                disposition = disposition,
                reason = reason,
                amount = recommendedFeeType?.let { resolveFeeAmount(it, period) },
                recommendedFeeType = recommendedFeeType,
                lastSentOn = lastSent,
            )
        }

        return BulkPreviewResult.of(BulkActionType.CONTRIBUTION_REMINDER, periodId, rows)
    }
}

@Component
class ExecuteBulkContributionReminderHandler(
    private val users: UserService,
    private val memberships: MembershipService,
    private val periods: ContributionPeriodService,
    private val contributions: ContributionService,
    private val reminders: ContributionReminderService,
) : CommandHandler<ExecuteBulkContributionReminderCommand, BulkActionResult> {
    override val commandType = ExecuteBulkContributionReminderCommand::class

    @Transactional
    override fun handle(command: ExecuteBulkContributionReminderCommand): BulkActionResult {
        val periodId = command.contributionPeriodId!!
        val period = periods.findById(periodId)
        val cutoffDate = command.cutoffDate!!
        val includedUserIds = command.includedUserIds

        var applied = 0
        var skipped = 0
        var queued = 0

        command.userIds.distinct().forEach { userId ->
            val user = users.findById(userId)

            // Get the current (active) membership; use the most recent by start date
            val activeMemberships = memberships.findByUserId(userId)
            val activeMembership = activeMemberships.maxByOrNull { it.startDate }

            // Resolve recommended fee type and check exclusion
            val memberType = activeMembership?.memberType ?: MemberType.REGULAR
            val membershipStart = activeMembership?.startDate
            val recommendedFeeType = resolveFeeType(memberType, membershipStart, cutoffDate)

            if (recommendedFeeType == null) {
                // Honorary: never send, always skip
                skipped++
                return@forEach
            }

            // Check if already paid; if included explicitly, send anyway
            val alreadyPaid = contributions.existsByUserIdAndPeriodId(userId, periodId)
            val shouldSend = !alreadyPaid || includedUserIds.contains(userId)

            if (!shouldSend) {
                skipped++
                return@forEach
            }

            // Skip if user has no email
            if (user.email.isBlank()) {
                skipped++
                return@forEach
            }

            // Resolve the € amount: use the operator's chosen fee type if overridden, else recommend
            val effectiveFeeType = command.feeTypeOverrides[userId] ?: recommendedFeeType
            val amountToSend = resolveFeeAmount(effectiveFeeType, period)

            // Create audit record with amount and due date
            val reminder = reminders.create(
                ContributionReminder(
                    user = user,
                    contributionPeriod = period,
                    amount = amountToSend,
                    paymentDueDate = command.paymentDueDate,
                )
            )

            // Enqueue email
            reminders.sendReminder(reminder)

            applied++
            queued++
        }

        return BulkActionResult(applied = applied, skipped = skipped, queued = queued)
    }
}

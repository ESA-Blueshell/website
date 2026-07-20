package net.blueshell.api.domain.contribution.application.command

import net.blueshell.api.domain.contribution.application.ContributionPeriodService
import net.blueshell.api.domain.contribution.application.ContributionService
import net.blueshell.api.domain.contribution.application.IncassoNotificationService
import net.blueshell.api.domain.contribution.command.ExecuteBulkIncassoNotificationCommand
import net.blueshell.api.domain.contribution.domain.service.resolveFeeAmount
import net.blueshell.api.domain.contribution.domain.service.resolveFeeType
import net.blueshell.api.domain.contribution.persistence.ContributionPeriod
import net.blueshell.api.domain.contribution.persistence.IncassoNotification
import net.blueshell.api.domain.user.application.MembershipService
import net.blueshell.api.domain.user.application.UserService
import net.blueshell.api.shared.command.CommandHandler
import net.blueshell.api.shared.dto.bulk.BulkActionResult
import net.blueshell.api.shared.dto.bulk.BulkRowDisposition
import net.blueshell.api.shared.dto.bulk.BulkRowReason
import net.blueshell.api.shared.enums.MemberType
import org.springframework.stereotype.Component
import org.springframework.transaction.annotation.Transactional
import java.time.LocalDate
import java.time.ZoneOffset

@Component
class ExecuteBulkIncassoNotificationHandler(
    private val users: UserService,
    private val memberships: MembershipService,
    private val periods: ContributionPeriodService,
    private val contributions: ContributionService,
    private val notifications: IncassoNotificationService,
) : CommandHandler<ExecuteBulkIncassoNotificationCommand, BulkActionResult> {
    override val commandType = ExecuteBulkIncassoNotificationCommand::class

    @Transactional
    override fun handle(command: ExecuteBulkIncassoNotificationCommand): BulkActionResult {
        val periodId = command.contributionPeriodId!!
        val period = periods.findById(periodId)
        val cutoffDate = command.cutoffDate!!
        val includedUserIds = command.includedUserIds

        val decisions = command.userIds.distinct().associateWith { userId ->
            decideIncasso(userId, periodId, period, cutoffDate, users, memberships, contributions, notifications)
        }

        validateFeeTypeOverrides(command.feeTypeOverrides, includedUserIds, decisions)

        var applied = 0
        var skipped = 0
        var queued = 0

        decisions.forEach { (userId, decision) ->
            val shouldSend = when (decision.disposition) {
                BulkRowDisposition.INCLUDED -> true
                BulkRowDisposition.WARNING -> userId in includedUserIds
                else -> false // EXCLUDED / SKIPPED (incl. NO_EMAIL)
            }
            if (!shouldSend) {
                skipped++
                return@forEach
            }

            val user = users.findById(userId)
            val effectiveFeeType = command.feeTypeOverrides[userId] ?: decision.recommendedFeeType!!
            val amountToSend = resolveFeeAmount(effectiveFeeType, period)

            val notification = notifications.create(
                IncassoNotification(
                    user = user,
                    contributionPeriod = period,
                    amount = amountToSend,
                    expectedIncassoDate = command.expectedIncassoDate,
                )
            )
            notifications.sendNotification(notification)

            applied++
            queued++
        }

        return BulkActionResult(applied = applied, skipped = skipped, queued = queued)
    }
}

/**
 * Decision function for the incasso-notification bulk action. Mirrors [decideReminder]
 * but adds the incasso-flag check. Called by both preview and execute.
 * See docs/proposals/bulk-actions/REDESIGN.md §3.
 */
internal fun decideIncasso(
    userId: Long,
    periodId: Long,
    period: ContributionPeriod,
    cutoffDate: LocalDate,
    users: UserService,
    memberships: MembershipService,
    contributions: ContributionService,
    notifications: IncassoNotificationService,
): EmailBulkDecision {
    val user = users.findById(userId)

    val activeMembership = memberships.findByUserId(userId).maxByOrNull { it.startDate }
    val memberType = activeMembership?.memberType ?: MemberType.REGULAR
    val membershipStart = activeMembership?.startDate
    val recommendedFeeType = resolveFeeType(memberType, membershipStart, cutoffDate)

    val hasIncassoEnabled = activeMembership?.incasso ?: false
    val alreadyPaid = contributions.existsByUserIdAndPeriodId(userId, periodId)
    val emailMissing = user.email.isBlank()
    val lastSent = notifications.findLastNotificationForUserAndPeriod(userId, periodId)?.createdAt
        ?.atZone(ZoneOffset.UTC)?.toLocalDate()

    val disposition: BulkRowDisposition
    val reason: BulkRowReason?
    when {
        recommendedFeeType == null -> {
            disposition = BulkRowDisposition.EXCLUDED
            reason = BulkRowReason.HONORARY
        }
        emailMissing -> {
            disposition = BulkRowDisposition.SKIPPED
            reason = BulkRowReason.NO_EMAIL
        }
        !hasIncassoEnabled -> {
            disposition = BulkRowDisposition.WARNING
            reason = BulkRowReason.INCASSO_MISMATCH
        }
        alreadyPaid -> {
            disposition = BulkRowDisposition.WARNING
            reason = BulkRowReason.ALREADY_PAID
        }
        else -> {
            disposition = BulkRowDisposition.INCLUDED
            reason = null
        }
    }

    return EmailBulkDecision(
        userId = userId,
        name = user.fullName,
        memberType = memberType,
        memberSince = membershipStart,
        disposition = disposition,
        reason = reason,
        recommendedFeeType = recommendedFeeType,
        amount = recommendedFeeType?.let { resolveFeeAmount(it, period) },
        lastSentOn = lastSent,
        emailMissing = emailMissing,
    )
}

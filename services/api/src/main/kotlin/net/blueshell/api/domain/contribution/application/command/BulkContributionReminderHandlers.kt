package net.blueshell.api.domain.contribution.application.command

import net.blueshell.api.domain.contribution.application.ContributionPeriodService
import net.blueshell.api.domain.contribution.application.ContributionReminderService
import net.blueshell.api.domain.contribution.application.ContributionService
import net.blueshell.api.domain.contribution.command.ExecuteBulkContributionReminderCommand
import net.blueshell.api.domain.contribution.domain.service.resolveFeeAmount
import net.blueshell.api.domain.contribution.domain.service.resolveFeeType
import net.blueshell.api.domain.contribution.persistence.ContributionPeriod
import net.blueshell.api.domain.contribution.persistence.ContributionReminder
import net.blueshell.api.domain.user.application.MembershipService
import net.blueshell.api.domain.user.application.UserService
import net.blueshell.api.shared.command.CommandHandler
import net.blueshell.api.shared.dto.bulk.BulkActionResult
import net.blueshell.api.shared.dto.bulk.BulkFeeType
import net.blueshell.api.shared.dto.bulk.BulkRowDisposition
import net.blueshell.api.shared.dto.bulk.BulkRowReason
import net.blueshell.api.shared.enums.MemberType
import org.springframework.stereotype.Component
import org.springframework.transaction.annotation.Transactional
import java.time.LocalDate
import java.time.ZoneOffset

/**
 * The single decision an email-style bulk action reaches for one user.
 * Computed by the execute handler to determine side effects.
 * The [decideReminder]/[decideIncasso] functions are shared decision logic.
 * See docs/proposals/bulk-actions/REDESIGN.md §3.
 */
data class EmailBulkDecision(
    val userId: Long,
    val name: String,
    val memberType: MemberType,
    val memberSince: LocalDate?,
    val disposition: BulkRowDisposition,
    val reason: BulkRowReason?,
    val recommendedFeeType: BulkFeeType?,
    val amount: Double?,
    val lastSentOn: LocalDate?,
    /** True when the user has no email; execute must skip even if operator re-includes. */
    val emailMissing: Boolean,
)

/**
 * Validate operator-supplied fee-type overrides against the computed decisions.
 * Rejects (HTTP 400) an override for a user who is EXCLUDED/HONORARY (no fee applies)
 * or who is not in the operator's included set. Missing override → recommended type.
 * See docs/proposals/bulk-actions/REDESIGN.md §3 (fee-override validation).
 */
internal fun validateFeeTypeOverrides(
    feeTypeOverrides: Map<Long, BulkFeeType>,
    includedUserIds: Set<Long>,
    decisionsByUser: Map<Long, EmailBulkDecision>,
) {
    feeTypeOverrides.keys.forEach { userId ->
        val decision = decisionsByUser[userId]
        if (decision == null || decision.recommendedFeeType == null) {
            throw org.springframework.web.server.ResponseStatusException(
                org.springframework.http.HttpStatus.BAD_REQUEST,
                "Fee-type override supplied for user $userId who is excluded from this action",
            )
        }
        if (userId !in includedUserIds) {
            throw org.springframework.web.server.ResponseStatusException(
                org.springframework.http.HttpStatus.BAD_REQUEST,
                "Fee-type override supplied for user $userId who is not included in this action",
            )
        }
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

        // Decide once for every selected user — same code path as preview.
        val decisions = command.userIds.distinct().associateWith { userId ->
            decideReminder(userId, periodId, period, cutoffDate, users, memberships, contributions, reminders)
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

            val reminder = reminders.create(
                ContributionReminder(
                    user = user,
                    contributionPeriod = period,
                    amount = amountToSend,
                    paymentDueDate = command.paymentDueDate,
                )
            )
            reminders.sendReminder(reminder)

            applied++
            queued++
        }

        return BulkActionResult(applied = applied, skipped = skipped, queued = queued)
    }
}

/**
 * Decision function for the contribution-reminder bulk action. Pure with respect to the
 * DB reads it performs (no writes). Called by both preview and execute.
 */
internal fun decideReminder(
    userId: Long,
    periodId: Long,
    period: ContributionPeriod,
    cutoffDate: LocalDate,
    users: UserService,
    memberships: MembershipService,
    contributions: ContributionService,
    reminders: ContributionReminderService,
): EmailBulkDecision {
    val user = users.findById(userId)

    // Current (active) membership — most recent by start date. This LATEST start is
    // what fee resolution keys off (NOT the earliest the FE derives), which is exactly
    // why reminder preview cannot be safely computed client-side.
    val activeMembership = memberships.findByUserId(userId).maxByOrNull { it.startDate }
    val memberType = activeMembership?.memberType ?: MemberType.REGULAR
    val membershipStart = activeMembership?.startDate
    val recommendedFeeType = resolveFeeType(memberType, membershipStart, cutoffDate)

    val alreadyPaid = contributions.existsByUserIdAndPeriodId(userId, periodId)
    val emailMissing = user.email.isBlank()
    val lastSent = reminders.findLastReminderForUserAndPeriod(userId, periodId)?.createdAt
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

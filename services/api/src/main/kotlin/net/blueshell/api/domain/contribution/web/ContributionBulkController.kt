package net.blueshell.api.domain.contribution.web

import io.swagger.v3.oas.annotations.tags.Tag
import jakarta.validation.Valid
import net.blueshell.api.domain.contribution.command.BulkContributionOperation
import net.blueshell.api.domain.contribution.command.ExecuteBulkContributionCommand
import net.blueshell.api.domain.contribution.command.ExecuteBulkContributionReminderCommand
import net.blueshell.api.domain.contribution.command.ExecuteBulkIncassoNotificationCommand
import net.blueshell.api.domain.contribution.web.dto.request.BulkContributionReminderExecuteRequest
import net.blueshell.api.domain.contribution.web.dto.request.BulkIncassoNotificationExecuteRequest
import net.blueshell.api.domain.contribution.web.dto.request.BulkMarkPaidRequest
import net.blueshell.api.domain.contribution.web.dto.request.BulkMarkUnpaidRequest
import net.blueshell.api.shared.command.CommandBus
import net.blueshell.api.shared.dto.bulk.BulkActionResult
import org.springframework.security.access.prepost.PreAuthorize
import org.springframework.web.bind.annotation.PostMapping
import org.springframework.web.bind.annotation.RequestBody
import org.springframework.web.bind.annotation.RestController

/**
 * Bulk actions over members: mark-paid/unpaid, send contribution reminders, send incasso
 * notifications. One submit endpoint per action with action-named paths. Mark-paid/unpaid
 * are execute-only (their preview is computed frontend-side); reminder/incasso keep a
 * preview endpoint that returns immutable server truth (no operator overrides), and an
 * execute endpoint that re-decides against the live DB and validates overrides. Board-only.
 * See docs/proposals/bulk-actions/REDESIGN.md §2.
 */
@RestController
@Tag(name = "Contributions")
class ContributionBulkController(private val commandBus: CommandBus) {

    // ===== Mark Paid / Unpaid (execute-only; preview is frontend-computed) =====

    @PreAuthorize("hasPermission('__NO_TARGET__', 'Contribution', 'write')")
    @PostMapping("/contributions/bulk/mark-paid")
    fun markPaid(@Valid @RequestBody request: BulkMarkPaidRequest): BulkActionResult =
        commandBus.dispatch(
            ExecuteBulkContributionCommand(
                userIds = request.userIds,
                contributionPeriodId = request.contributionPeriodId,
                operation = BulkContributionOperation.PAID,
            )
        )

    @PreAuthorize("hasPermission('__NO_TARGET__', 'Contribution', 'write')")
    @PostMapping("/contributions/bulk/mark-unpaid")
    fun markUnpaid(@Valid @RequestBody request: BulkMarkUnpaidRequest): BulkActionResult =
        commandBus.dispatch(
            ExecuteBulkContributionCommand(
                userIds = request.userIds,
                contributionPeriodId = request.contributionPeriodId,
                operation = BulkContributionOperation.UNPAID,
            )
        )

    // ===== Contribution Reminders =====

    @PreAuthorize("hasPermission('__NO_TARGET__', 'ContributionReminder', 'write')")
    @PostMapping("/contributionReminders/bulk/execute")
    fun executeBulkReminder(@Valid @RequestBody request: BulkContributionReminderExecuteRequest): BulkActionResult =
        commandBus.dispatch(
            ExecuteBulkContributionReminderCommand(
                userIds = request.userIds,
                contributionPeriodId = request.contributionPeriodId,
                cutoffDate = request.cutoffDate,
                paymentDueDate = request.paymentDueDate,
                includedUserIds = request.includedUserIds,
                feeTypeOverrides = request.feeTypeOverrides,
            )
        )

    // ===== Incasso Notifications =====

    @PreAuthorize("hasPermission('__NO_TARGET__', 'Contribution', 'write')")
    @PostMapping("/incassoNotifications/bulk/execute")
    fun executeBulkIncassoNotification(
        @Valid @RequestBody request: BulkIncassoNotificationExecuteRequest,
    ): BulkActionResult =
        commandBus.dispatch(
            ExecuteBulkIncassoNotificationCommand(
                userIds = request.userIds,
                contributionPeriodId = request.contributionPeriodId,
                cutoffDate = request.cutoffDate,
                expectedIncassoDate = request.expectedIncassoDate,
                includedUserIds = request.includedUserIds,
                feeTypeOverrides = request.feeTypeOverrides,
            )
        )
}

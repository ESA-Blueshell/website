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
 * notifications. One execute endpoint per action with action-named paths. All preview
 * computation now happens frontend-side; execute endpoints are the source of truth and
 * re-validate against the live DB. Board-only. See docs/proposals/bulk-actions/REDESIGN.md §2.
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

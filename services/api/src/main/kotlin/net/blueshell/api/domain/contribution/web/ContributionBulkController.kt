package net.blueshell.api.domain.contribution.web

import io.swagger.v3.oas.annotations.tags.Tag
import jakarta.validation.Valid
import net.blueshell.api.domain.contribution.command.BulkContributionOperation
import net.blueshell.api.domain.contribution.command.ExecuteBulkContributionCommand
import net.blueshell.api.domain.contribution.web.dto.request.BulkMarkPaidRequest
import net.blueshell.api.domain.contribution.web.dto.request.BulkMarkUnpaidRequest
import net.blueshell.api.shared.command.CommandBus
import net.blueshell.api.shared.dto.bulk.BulkActionResult
import org.springframework.security.access.prepost.PreAuthorize
import org.springframework.web.bind.annotation.PostMapping
import org.springframework.web.bind.annotation.RequestBody
import org.springframework.web.bind.annotation.RestController

/**
 * Bulk contribution actions, board-only. Which rows an action will touch is worked out in
 * the browser from data the member table already holds; these endpoints are the source of
 * truth and re-check every row against the database before writing.
 */
@RestController
@Tag(name = "Contributions")
class ContributionBulkController(
    private val commandBus: CommandBus,
) {

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
}

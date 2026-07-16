package net.blueshell.api.domain.contribution.web

import io.swagger.v3.oas.annotations.tags.Tag
import jakarta.validation.Valid
import net.blueshell.api.domain.contribution.command.ExecuteBulkContributionCommand
import net.blueshell.api.domain.contribution.command.PreviewBulkContributionCommand
import net.blueshell.api.domain.contribution.web.dto.request.BulkContributionRequest
import net.blueshell.api.shared.command.CommandBus
import net.blueshell.api.shared.dto.bulk.BulkActionResult
import net.blueshell.api.shared.dto.bulk.BulkPreviewResult
import org.springframework.security.access.prepost.PreAuthorize
import org.springframework.web.bind.annotation.PostMapping
import org.springframework.web.bind.annotation.RequestBody
import org.springframework.web.bind.annotation.RestController

/**
 * Bulk mark-paid / mark-unpaid over a set of users for a contribution period.
 * Two-phase: `preview` returns the shared envelope (who will change vs is
 * skipped); `execute` applies it in a single request. Board-only.
 */
@RestController
@Tag(name = "Contributions")
class ContributionBulkController(private val commandBus: CommandBus) {

    @PreAuthorize("hasPermission('__NO_TARGET__', 'Contribution', 'write')")
    @PostMapping("/contributions/bulk/preview")
    fun previewBulk(@Valid @RequestBody request: BulkContributionRequest): BulkPreviewResult =
        commandBus.dispatch(
            PreviewBulkContributionCommand(
                userIds = request.userIds,
                contributionPeriodId = request.contributionPeriodId,
                operation = request.operation,
            )
        )

    @PreAuthorize("hasPermission('__NO_TARGET__', 'Contribution', 'write')")
    @PostMapping("/contributions/bulk/execute")
    fun executeBulk(@Valid @RequestBody request: BulkContributionRequest): BulkActionResult =
        commandBus.dispatch(
            ExecuteBulkContributionCommand(
                userIds = request.userIds,
                contributionPeriodId = request.contributionPeriodId,
                operation = request.operation,
            )
        )
}

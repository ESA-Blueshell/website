package net.blueshell.api.contribution.web

import io.swagger.v3.oas.annotations.tags.Tag
import jakarta.validation.Valid
import net.blueshell.api.contribution.domain.BulkContributionOperation
import net.blueshell.api.contribution.domain.BulkContributionUseCases
import net.blueshell.api.shared.dto.bulk.BulkActionResult
import org.springframework.security.access.prepost.PreAuthorize
import org.springframework.web.bind.annotation.PostMapping
import org.springframework.web.bind.annotation.RequestBody
import org.springframework.web.bind.annotation.RestController

/**
 * Records contributions for a set of users at once, board-only.
 *
 * Both endpoints apply a selection whole. A selection naming users the action cannot
 * touch is refused with 409 and the offending ids, so the caller can reload the rows
 * it can no longer trust rather than guess which of them moved.
 */
@RestController
@Tag(name = "Contributions")
class ContributionBulkController(
    private val useCases: BulkContributionUseCases,
) {

    @PreAuthorize("hasPermission('__NO_TARGET__', 'Contribution', 'write')")
    @PostMapping("/contributions/bulk/mark-paid")
    fun markPaid(@Valid @RequestBody request: BulkMarkPaidRequest): BulkActionResult =
        useCases.execute(
            userIds = request.userIds,
            contributionPeriodId = requireNotNull(request.contributionPeriodId),
            operation = BulkContributionOperation.PAID,
        )

    @PreAuthorize("hasPermission('__NO_TARGET__', 'Contribution', 'write')")
    @PostMapping("/contributions/bulk/mark-unpaid")
    fun markUnpaid(@Valid @RequestBody request: BulkMarkUnpaidRequest): BulkActionResult =
        useCases.execute(
            userIds = request.userIds,
            contributionPeriodId = requireNotNull(request.contributionPeriodId),
            operation = BulkContributionOperation.UNPAID,
        )
}

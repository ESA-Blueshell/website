package net.blueshell.api.domain.contribution.web.dto.request

import io.swagger.v3.oas.annotations.media.Schema
import jakarta.validation.constraints.NotEmpty
import jakarta.validation.constraints.NotNull
import jakarta.validation.constraints.Positive

/**
 * Execute-only request for the mark-paid bulk action. The operation is implied by the
 * action-named path (`/contributions/bulk/mark-paid`), so no `operation` field is sent.
 * There is no preview endpoint — the frontend computes the mark-paid preview locally
 * from its paid-user set. See docs/proposals/bulk-actions/REDESIGN.md §2.
 */
@Schema(name = "BulkMarkPaidRequest")
data class BulkMarkPaidRequest(
    @field:NotEmpty(message = "At least one user ID is required")
    val userIds: List<@Positive Long> = emptyList(),

    @field:NotNull(message = "Contribution period ID is required")
    @field:Positive(message = "Contribution period ID must be positive")
    val contributionPeriodId: Long? = null,
)

/**
 * Execute-only request for the mark-unpaid bulk action. Mirror of [BulkMarkPaidRequest]
 * for the `/contributions/bulk/mark-unpaid` path.
 */
@Schema(name = "BulkMarkUnpaidRequest")
data class BulkMarkUnpaidRequest(
    @field:NotEmpty(message = "At least one user ID is required")
    val userIds: List<@Positive Long> = emptyList(),

    @field:NotNull(message = "Contribution period ID is required")
    @field:Positive(message = "Contribution period ID must be positive")
    val contributionPeriodId: Long? = null,
)

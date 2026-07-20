package net.blueshell.api.domain.contribution.web.dto.request

import io.swagger.v3.oas.annotations.media.Schema
import jakarta.validation.constraints.NotEmpty
import jakarta.validation.constraints.NotNull
import jakarta.validation.constraints.Positive
import net.blueshell.api.shared.dto.bulk.BulkFeeType
import java.time.LocalDate

/**
 * Execute request for the contribution-reminder bulk action. Carries the operator's
 * re-include set and per-user fee-type overrides. The server re-decides against the
 * live DB and validates the overrides (rejects excluded/non-included users).
 */
@Schema(name = "BulkContributionReminderExecuteRequest")
data class BulkContributionReminderExecuteRequest(
    @field:NotEmpty(message = "At least one user ID is required")
    val userIds: List<@Positive Long> = emptyList(),

    @field:NotNull(message = "Contribution period ID is required")
    @field:Positive(message = "Contribution period ID must be positive")
    val contributionPeriodId: Long? = null,

    @field:NotNull(message = "Cutoff date is required")
    val cutoffDate: LocalDate? = null,

    @field:NotNull(message = "Payment due date is required")
    val paymentDueDate: LocalDate? = null,

    /** User IDs to include (re-includes already-paid WARNING rows). */
    val includedUserIds: Set<Long> = emptySet(),

    /**
     * Per-user fee type overrides (userId -> BulkFeeType). The server resolves the €
     * from the period's fee for the chosen type; missing → recommended type.
     */
    val feeTypeOverrides: Map<Long, BulkFeeType> = emptyMap(),
)

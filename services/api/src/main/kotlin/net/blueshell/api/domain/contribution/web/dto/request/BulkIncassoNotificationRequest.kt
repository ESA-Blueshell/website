package net.blueshell.api.domain.contribution.web.dto.request

import io.swagger.v3.oas.annotations.media.Schema
import jakarta.validation.constraints.NotEmpty
import jakarta.validation.constraints.NotNull
import jakarta.validation.constraints.Positive
import net.blueshell.api.shared.dto.bulk.BulkFeeType
import java.time.LocalDate

/**
 * Execute request for the incasso-notification bulk action. Carries the operator's
 * re-include set and per-user fee-type overrides.
 */
@Schema(name = "BulkIncassoNotificationExecuteRequest")
data class BulkIncassoNotificationExecuteRequest(
    @field:NotEmpty(message = "At least one user ID is required")
    val userIds: List<@Positive Long> = emptyList(),

    @field:NotNull(message = "Contribution period ID is required")
    @field:Positive(message = "Contribution period ID must be positive")
    val contributionPeriodId: Long? = null,

    @field:NotNull(message = "Cutoff date is required")
    val cutoffDate: LocalDate? = null,

    @field:NotNull(message = "Expected incasso date is required")
    val expectedIncassoDate: LocalDate? = null,

    /** User IDs to include (re-includes non-incasso / already-paid WARNING rows). */
    val includedUserIds: Set<Long> = emptySet(),

    /**
     * Per-user fee type overrides (userId -> BulkFeeType); missing → recommended type.
     */
    val feeTypeOverrides: Map<Long, BulkFeeType> = emptyMap(),
)

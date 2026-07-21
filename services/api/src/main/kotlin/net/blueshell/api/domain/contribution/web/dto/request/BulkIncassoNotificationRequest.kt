package net.blueshell.api.domain.contribution.web.dto.request

import io.swagger.v3.oas.annotations.media.Schema
import jakarta.validation.constraints.Future
import jakarta.validation.constraints.NotEmpty
import jakarta.validation.constraints.NotNull
import jakarta.validation.constraints.Positive
import jakarta.validation.constraints.Size
import net.blueshell.api.shared.dto.bulk.BulkFeeType
import java.time.LocalDate

/**
 * Execute request for the incasso-notification bulk action. Carries the operator's
 * re-include set and per-user fee-type overrides.
 */
@Schema(name = "BulkIncassoNotificationExecuteRequest")
data class BulkIncassoNotificationExecuteRequest(
    @field:NotEmpty(message = "At least one user ID is required")
    @field:Size(min = 1, max = 1000, message = "userIds must contain between 1 and 1000 entries")
    val userIds: List<@Positive Long> = emptyList(),

    @field:NotNull(message = "Contribution period ID is required")
    @field:Positive(message = "Contribution period ID must be positive")
    val contributionPeriodId: Long? = null,

    @field:NotNull(message = "Cutoff date is required")
    val cutoffDate: LocalDate? = null,

    @field:NotNull(message = "Expected incasso date is required")
    @field:Future(message = "Expected incasso date must be in the future")
    val expectedIncassoDate: LocalDate? = null,

    /** User IDs to include (re-includes non-incasso / already-paid WARNING rows). */
    @field:Size(max = 1000, message = "includedUserIds must not exceed 1000 entries")
    val includedUserIds: Set<Long> = emptySet(),

    /**
     * Per-user fee type overrides (userId -> BulkFeeType); missing → recommended type.
     */
    @field:Size(max = 1000, message = "feeTypeOverrides must not exceed 1000 entries")
    val feeTypeOverrides: Map<Long, BulkFeeType> = emptyMap(),
)

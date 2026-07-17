package net.blueshell.api.domain.contribution.web.dto.request

import io.swagger.v3.oas.annotations.media.Schema
import jakarta.validation.constraints.NotEmpty
import jakarta.validation.constraints.NotNull
import net.blueshell.api.shared.dto.bulk.BulkFeeType
import java.time.LocalDate

@Schema(name = "BulkContributionReminderRequest")
data class BulkContributionReminderRequest(
    @field:NotEmpty
    var userIds: List<Long> = emptyList(),

    @field:NotNull
    var contributionPeriodId: Long? = null,

    @field:NotNull(message = "Cutoff date is required")
    var cutoffDate: LocalDate? = null,

    @field:NotNull(message = "Payment due date is required")
    var paymentDueDate: LocalDate? = null,

    /** User IDs to include (for execute; re-includes already-paid users). Empty for preview. */
    var includedUserIds: Set<Long> = emptySet(),

    /**
     * Per-user fee type overrides for execute (userId -> BulkFeeType).
     * The server resolves the € from the period's fee for the chosen type.
     * If a user has no override, the server uses their recommended fee type.
     */
    var feeTypeOverrides: Map<Long, BulkFeeType> = emptyMap(),
)

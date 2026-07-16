package net.blueshell.api.domain.contribution.command

import jakarta.validation.constraints.NotEmpty
import jakarta.validation.constraints.NotNull
import jakarta.validation.constraints.Positive
import net.blueshell.api.shared.command.Command
import net.blueshell.api.shared.dto.bulk.BulkActionResult
import net.blueshell.api.shared.dto.bulk.BulkPreviewResult
import java.time.LocalDate

data class PreviewBulkContributionReminderCommand(
    @field:NotEmpty(message = "At least one user ID is required")
    val userIds: List<@Positive Long>,
    @field:NotNull(message = "Contribution period ID is required")
    @field:Positive(message = "Contribution period ID must be positive")
    val contributionPeriodId: Long?,
    @field:NotNull(message = "Cutoff date is required")
    val cutoffDate: LocalDate?,
    @field:NotNull(message = "Payment due date is required")
    val paymentDueDate: LocalDate?,
) : Command<BulkPreviewResult>

data class ExecuteBulkContributionReminderCommand(
    @field:NotEmpty(message = "At least one user ID is required")
    val userIds: List<@Positive Long>,
    @field:NotNull(message = "Contribution period ID is required")
    @field:Positive(message = "Contribution period ID must be positive")
    val contributionPeriodId: Long?,
    @field:NotNull(message = "Cutoff date is required")
    val cutoffDate: LocalDate?,
    @field:NotNull(message = "Payment due date is required")
    val paymentDueDate: LocalDate?,
    /** User IDs to include (re-includes those marked as already-paid by default). */
    val includedUserIds: Set<Long> = emptySet(),
    /** Per-user amount overrides: userId -> resolved amount in euros. */
    val amountOverrides: Map<Long, Double> = emptyMap(),
) : Command<BulkActionResult>

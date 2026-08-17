package net.blueshell.api.domain.contribution.command

import jakarta.validation.constraints.NotEmpty
import jakarta.validation.constraints.NotNull
import jakarta.validation.constraints.Positive
import net.blueshell.api.shared.command.Command
import net.blueshell.api.shared.dto.bulk.BulkActionResult

/** Which paid-state a bulk contribution action drives. */
enum class BulkContributionOperation { PAID, UNPAID }

data class ExecuteBulkContributionCommand(
    @field:NotEmpty(message = "At least one user ID is required")
    val userIds: List<@Positive Long>,
    @field:NotNull(message = "Contribution period ID is required")
    @field:Positive(message = "Contribution period ID must be positive")
    val contributionPeriodId: Long?,
    @field:NotNull(message = "Operation is required")
    val operation: BulkContributionOperation?,
) : Command<BulkActionResult>

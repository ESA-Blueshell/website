package net.blueshell.api.domain.contribution.command

import jakarta.validation.constraints.NotNull
import jakarta.validation.constraints.Positive
import net.blueshell.api.domain.contribution.command.result.ContributionResult
import net.blueshell.api.shared.command.Command

data class CreateContributionCommand(
    @field:NotNull(message = "User ID is required")
    @field:Positive(message = "User ID must be positive")
    val userId: Long?,
    @field:NotNull(message = "Contribution period ID is required")
    @field:Positive(message = "Contribution period ID must be positive")
    val contributionPeriodId: Long?
) : Command<ContributionResult>

data class FindContributionsCommand(
    @field:NotNull(message = "Contribution period ID is required")
    @field:Positive(message = "Contribution period ID must be positive")
    val contributionPeriodId: Long?
) : Command<List<ContributionResult>>

data class DeleteContributionCommand(
    @field:NotNull(message = "User ID is required")
    @field:Positive(message = "User ID must be positive")
    val userId: Long?,
    @field:NotNull(message = "Contribution period ID is required")
    @field:Positive(message = "Contribution period ID must be positive")
    val contributionPeriodId: Long?
) : Command<Unit>

data class FindContributionsByPeriodIdCommand(
    @field:NotNull(message = "Period ID is required")
    @field:Positive(message = "Period ID must be positive")
    val periodId: Long?
) : Command<List<ContributionResult>>

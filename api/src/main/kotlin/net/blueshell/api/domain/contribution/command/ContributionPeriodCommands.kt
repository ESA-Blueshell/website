package net.blueshell.api.domain.contribution.command

import jakarta.validation.constraints.Future
import jakarta.validation.constraints.NotNull
import jakarta.validation.constraints.Positive
import jakarta.validation.constraints.PositiveOrZero
import net.blueshell.api.domain.contribution.command.result.ContributionPeriodResult
import net.blueshell.api.shared.command.Command
import java.time.LocalDate

class FindContributionPeriodsCommand : Command<List<ContributionPeriodResult>>

class FindCurrentContributionPeriodCommand : Command<ContributionPeriodResult?>

data class CreateContributionPeriodCommand(
    @field:NotNull(message = "Start date is required")
    val startDate: LocalDate?,
    @field:NotNull(message = "End date is required")
    @field:Future(message = "End date must be in the future")
    val endDate: LocalDate?,
    @field:NotNull(message = "Half-year fee is required")
    @field:Positive(message = "Half-year fee must be positive")
    val halfYearFee: Double?,
    @field:NotNull(message = "Full-year fee is required")
    @field:Positive(message = "Full-year fee must be positive")
    val fullYearFee: Double?,
    @field:NotNull(message = "Alumni fee is required")
    @field:PositiveOrZero(message = "Alumni fee must be positive or zero")
    val alumniFee: Double?,
    val listId: Long?
) : Command<ContributionPeriodResult>

data class UpdateContributionPeriodCommand(
    @field:NotNull(message = "Contribution period ID is required")
    @field:Positive(message = "Contribution period ID must be positive")
    val id: Long?,
    @field:NotNull(message = "Start date is required")
    val startDate: LocalDate?,
    @field:NotNull(message = "End date is required")
    val endDate: LocalDate?,
    @field:NotNull(message = "Half-year fee is required")
    @field:Positive(message = "Half-year fee must be positive")
    val halfYearFee: Double?,
    @field:NotNull(message = "Full-year fee is required")
    @field:Positive(message = "Full-year fee must be positive")
    val fullYearFee: Double?,
    @field:NotNull(message = "Alumni fee is required")
    @field:PositiveOrZero(message = "Alumni fee must be positive or zero")
    val alumniFee: Double?,
    val listId: Long?,
    @field:NotNull(message = "Version is required for optimistic locking")
    val version: Long
) : Command<ContributionPeriodResult>

data class DeleteContributionPeriodByIdCommand(
    @field:NotNull(message = "Contribution period ID is required")
    @field:Positive(message = "Contribution period ID must be positive")
    val id: Long?
) : Command<Unit>

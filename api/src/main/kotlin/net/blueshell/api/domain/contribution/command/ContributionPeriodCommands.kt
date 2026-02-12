package net.blueshell.api.domain.contribution.command

import net.blueshell.api.domain.contribution.persistence.ContributionPeriod
import net.blueshell.api.shared.command.Command
import java.time.LocalDate

class FindContributionPeriodsCommand : Command<MutableList<ContributionPeriod>>

class FindCurrentContributionPeriodCommand : Command<ContributionPeriod>

data class CreateContributionPeriodCommand(
    val startDate: LocalDate,
    val endDate: LocalDate,
    val halfYearFee: Double,
    val fullYearFee: Double,
    val alumniFee: Double,
    val listId: Long?
) : Command<ContributionPeriod>

data class UpdateContributionPeriodCommand(
    val id: Long,
    val startDate: LocalDate,
    val endDate: LocalDate,
    val halfYearFee: Double,
    val fullYearFee: Double,
    val alumniFee: Double,
    val listId: Long?,
    val version: Long?
) : Command<ContributionPeriod>

data class DeleteContributionPeriodByIdCommand(
    val id: Long
) : Command<Unit>

package net.blueshell.api.domain.contribution.command

import net.blueshell.api.domain.contribution.persistence.ContributionPeriod
import net.blueshell.api.domain.contribution.web.dto.ContributionPeriodDTO
import net.blueshell.api.shared.command.Command

class FindContributionPeriodsCommand : Command<MutableList<ContributionPeriod>>

class FindCurrentContributionPeriodCommand : Command<ContributionPeriod>

data class CreateContributionPeriodCommand(
    val dto: ContributionPeriodDTO
) : Command<ContributionPeriod>

data class UpdateContributionPeriodCommand(
    val id: Long,
    val dto: ContributionPeriodDTO
) : Command<ContributionPeriod>

data class DeleteContributionPeriodByIdCommand(
    val id: Long
) : Command<Unit>

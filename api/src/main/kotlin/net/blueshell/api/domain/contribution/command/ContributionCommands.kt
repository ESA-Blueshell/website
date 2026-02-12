package net.blueshell.api.domain.contribution.command

import net.blueshell.api.domain.contribution.persistence.Contribution
import net.blueshell.api.domain.contribution.web.dto.ContributionDTO
import net.blueshell.api.shared.command.Command

data class CreateContributionCommand(
    val dto: ContributionDTO
) : Command<Contribution>

data class FindContributionsCommand(
    val contributionPeriodId: Long
) : Command<MutableList<Contribution>>

data class DeleteContributionCommand(
    val userId: Long,
    val contributionPeriodId: Long
) : Command<Unit>

data class FindContributionsByPeriodIdCommand(
    val periodId: Long
) : Command<MutableList<Contribution>>

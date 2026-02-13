package net.blueshell.api.domain.contribution.application.command

import net.blueshell.api.domain.contribution.application.ContributionPeriodService
import net.blueshell.api.domain.contribution.application.ContributionService
import net.blueshell.api.domain.contribution.command.CreateContributionCommand
import net.blueshell.api.domain.contribution.command.DeleteContributionCommand
import net.blueshell.api.domain.contribution.command.FindContributionsByPeriodIdCommand
import net.blueshell.api.domain.contribution.command.FindContributionsCommand
import net.blueshell.api.domain.contribution.command.result.ContributionResult
import net.blueshell.api.domain.contribution.command.result.toContributionResults
import net.blueshell.api.domain.contribution.command.result.toResult
import net.blueshell.api.domain.contribution.persistence.Contribution
import net.blueshell.api.domain.user.application.UserService
import net.blueshell.api.shared.command.CommandHandler
import org.springframework.stereotype.Component

@Component
class CreateContributionHandler(
    private val service: ContributionService,
    private val users: UserService,
    private val contributionPeriods: ContributionPeriodService
) : CommandHandler<CreateContributionCommand, ContributionResult> {
    override val commandType = CreateContributionCommand::class

    override fun handle(command: CreateContributionCommand): ContributionResult {
        var contribution = Contribution()
        contribution.user = users.findById(command.userId!!)
        contribution.contributionPeriod = contributionPeriods.findById(command.contributionPeriodId!!)
        contribution = service.create(contribution)
        return contribution.toResult()
    }
}

@Component
class FindContributionsHandler(
    private val service: ContributionService
) : CommandHandler<FindContributionsCommand, List<ContributionResult>> {
    override val commandType = FindContributionsCommand::class

    override fun handle(command: FindContributionsCommand): List<ContributionResult> {
        return service.findByContributionPeriodId(command.contributionPeriodId!!).toContributionResults()
    }
}

@Component
class DeleteContributionHandler(
    private val service: ContributionService
) : CommandHandler<DeleteContributionCommand, Unit> {
    override val commandType = DeleteContributionCommand::class

    override fun handle(command: DeleteContributionCommand) {
        service.deleteById(Contribution.Id(command.userId, command.contributionPeriodId!!))
    }
}

@Component
class FindContributionsByPeriodIdHandler(
    private val service: ContributionService
) : CommandHandler<FindContributionsByPeriodIdCommand, List<ContributionResult>> {
    override val commandType = FindContributionsByPeriodIdCommand::class

    override fun handle(command: FindContributionsByPeriodIdCommand): List<ContributionResult> {
        return service.findByContributionPeriodId(command.periodId!!).toContributionResults()
    }
}

package net.blueshell.api.domain.contribution.application.command

import net.blueshell.api.domain.contribution.application.ContributionService
import net.blueshell.api.domain.contribution.command.CreateContributionCommand
import net.blueshell.api.domain.contribution.command.DeleteContributionCommand
import net.blueshell.api.domain.contribution.command.FindContributionsByPeriodIdCommand
import net.blueshell.api.domain.contribution.command.FindContributionsCommand
import net.blueshell.api.domain.contribution.persistence.Contribution
import net.blueshell.api.domain.contribution.persistence.ContributionPeriod
import net.blueshell.api.shared.model.asRef
import net.blueshell.api.domain.user.persistence.User
import net.blueshell.api.shared.command.CommandHandler
import org.springframework.stereotype.Component

@Component
class CreateContributionHandler(
    private val service: ContributionService
) : CommandHandler<CreateContributionCommand, Contribution> {
    override val commandType = CreateContributionCommand::class

    override fun handle(command: CreateContributionCommand): Contribution {
        var contribution = Contribution()
        contribution.user = User::class.asRef(command.userId)
        contribution.contributionPeriod = ContributionPeriod::class.asRef(command.contributionPeriodId)
        contribution = service.create(contribution)
        return contribution
    }
}

@Component
class FindContributionsHandler(
    private val service: ContributionService
) : CommandHandler<FindContributionsCommand, MutableList<Contribution>> {
    override val commandType = FindContributionsCommand::class

    override fun handle(command: FindContributionsCommand): MutableList<Contribution> {
        return service.findByContributionPeriodId(command.contributionPeriodId)
    }
}

@Component
class DeleteContributionHandler(
    private val service: ContributionService
) : CommandHandler<DeleteContributionCommand, Unit> {
    override val commandType = DeleteContributionCommand::class

    override fun handle(command: DeleteContributionCommand) {
        service.deleteById(Contribution.Id(command.userId, command.contributionPeriodId))
    }
}

@Component
class FindContributionsByPeriodIdHandler(
    private val service: ContributionService
) : CommandHandler<FindContributionsByPeriodIdCommand, MutableList<Contribution>> {
    override val commandType = FindContributionsByPeriodIdCommand::class

    override fun handle(command: FindContributionsByPeriodIdCommand): MutableList<Contribution> {
        return service.findByContributionPeriodId(command.periodId)
    }
}

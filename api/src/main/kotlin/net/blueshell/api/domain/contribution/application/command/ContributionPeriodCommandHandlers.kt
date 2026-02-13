package net.blueshell.api.domain.contribution.application.command

import net.blueshell.api.domain.contribution.application.ContributionPeriodService
import net.blueshell.api.domain.contribution.command.*
import net.blueshell.api.domain.contribution.command.result.ContributionPeriodResult
import net.blueshell.api.domain.contribution.command.result.toContributionPeriodResults
import net.blueshell.api.domain.contribution.command.result.toResult
import net.blueshell.api.domain.contribution.persistence.ContributionPeriod
import net.blueshell.api.shared.command.CommandHandler
import org.springframework.stereotype.Component

@Component
class FindContributionPeriodsHandler(
    private val service: ContributionPeriodService
) : CommandHandler<FindContributionPeriodsCommand, List<ContributionPeriodResult>> {
    override val commandType = FindContributionPeriodsCommand::class

    override fun handle(command: FindContributionPeriodsCommand): List<ContributionPeriodResult> {
        return service.findAll().toContributionPeriodResults()
    }
}

@Component
class FindCurrentContributionPeriodHandler(
    private val service: ContributionPeriodService
) : CommandHandler<FindCurrentContributionPeriodCommand, ContributionPeriodResult> {
    override val commandType = FindCurrentContributionPeriodCommand::class

    override fun handle(command: FindCurrentContributionPeriodCommand): ContributionPeriodResult {
        return service.findLatest().toResult()
    }
}

@Component
class CreateContributionPeriodHandler(
    private val service: ContributionPeriodService
) : CommandHandler<CreateContributionPeriodCommand, ContributionPeriodResult> {
    override val commandType = CreateContributionPeriodCommand::class

    override fun handle(command: CreateContributionPeriodCommand): ContributionPeriodResult {
        var contributionPeriod = ContributionPeriod()
        contributionPeriod.startDate = command.startDate!!
        contributionPeriod.endDate = command.endDate!!
        contributionPeriod.halfYearFee = command.halfYearFee!!
        contributionPeriod.fullYearFee = command.fullYearFee!!
        contributionPeriod.alumniFee = command.alumniFee!!
        contributionPeriod.listId = command.listId
        contributionPeriod = service.create(contributionPeriod)
        return contributionPeriod.toResult()
    }
}

@Component
class UpdateContributionPeriodHandler(
    private val service: ContributionPeriodService
) : CommandHandler<UpdateContributionPeriodCommand, ContributionPeriodResult> {
    override val commandType = UpdateContributionPeriodCommand::class

    override fun handle(command: UpdateContributionPeriodCommand): ContributionPeriodResult {
        var contributionPeriod = service.findById(command.id!!)
        contributionPeriod.startDate = command.startDate!!
        contributionPeriod.endDate = command.endDate!!
        contributionPeriod.halfYearFee = command.halfYearFee!!
        contributionPeriod.fullYearFee = command.fullYearFee!!
        contributionPeriod.alumniFee = command.alumniFee!!
        contributionPeriod.listId = command.listId
        command.version?.let { contributionPeriod.version = it }
        contributionPeriod = service.update(contributionPeriod)
        return contributionPeriod.toResult()
    }
}

@Component
class DeleteContributionPeriodByIdHandler(
    private val service: ContributionPeriodService
) : CommandHandler<DeleteContributionPeriodByIdCommand, Unit> {
    override val commandType = DeleteContributionPeriodByIdCommand::class

    override fun handle(command: DeleteContributionPeriodByIdCommand) {
        service.deleteById(command.id!!)
    }
}

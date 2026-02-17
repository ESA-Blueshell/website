package net.blueshell.api.domain.sponsor.application.command

import net.blueshell.api.domain.sponsor.application.SponsorService
import net.blueshell.api.domain.sponsor.command.*
import net.blueshell.api.domain.sponsor.command.result.SponsorResult
import net.blueshell.api.domain.sponsor.command.result.toResult
import net.blueshell.api.domain.sponsor.command.result.toResults
import net.blueshell.api.domain.sponsor.persistence.Sponsor
import net.blueshell.api.shared.command.CommandHandler
import org.springframework.stereotype.Component

@Component
class FindSponsorsHandler(
    private val service: SponsorService
) : CommandHandler<FindSponsorsCommand, List<SponsorResult>> {
    override val commandType = FindSponsorsCommand::class

    override fun handle(command: FindSponsorsCommand): List<SponsorResult> {
        return service.findAll().toResults()
    }
}

@Component
class CreateSponsorHandler(
    private val service: SponsorService
) : CommandHandler<CreateSponsorCommand, SponsorResult> {
    override val commandType = CreateSponsorCommand::class

    override fun handle(command: CreateSponsorCommand): SponsorResult {
        var sponsor = Sponsor(
            name = command.name!!,
            description = command.description!!
        )
        sponsor = service.create(sponsor)
        return sponsor.toResult()
    }
}

@Component
class UpdateSponsorHandler(
    private val service: SponsorService
) : CommandHandler<UpdateSponsorCommand, SponsorResult> {
    override val commandType = UpdateSponsorCommand::class

    override fun handle(command: UpdateSponsorCommand): SponsorResult {
        var sponsor = service.findById(command.id!!).apply {
            name = command.name!!
            description = command.description!!
            version = command.version
        }
        sponsor = service.update(sponsor)
        return sponsor.toResult()
    }
}

@Component
class FindSponsorByIdHandler(
    private val service: SponsorService
) : CommandHandler<FindSponsorByIdCommand, SponsorResult> {
    override val commandType = FindSponsorByIdCommand::class

    override fun handle(command: FindSponsorByIdCommand): SponsorResult {
        return service.findById(command.id!!).toResult()
    }
}

@Component
class DeleteSponsorByIdHandler(
    private val service: SponsorService
) : CommandHandler<DeleteSponsorByIdCommand, Unit> {
    override val commandType = DeleteSponsorByIdCommand::class

    override fun handle(command: DeleteSponsorByIdCommand) {
        service.deleteById(command.id!!)
    }
}

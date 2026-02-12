package net.blueshell.api.domain.sponsor.application.command

import net.blueshell.api.domain.sponsor.application.SponsorService
import net.blueshell.api.domain.sponsor.command.CreateSponsorCommand
import net.blueshell.api.domain.sponsor.command.DeleteSponsorByIdCommand
import net.blueshell.api.domain.sponsor.command.FindSponsorByIdCommand
import net.blueshell.api.domain.sponsor.command.FindSponsorsCommand
import net.blueshell.api.domain.sponsor.command.UpdateSponsorCommand
import net.blueshell.api.domain.sponsor.persistence.Sponsor
import net.blueshell.api.shared.command.CommandHandler
import org.springframework.stereotype.Component

@Component
class FindSponsorsHandler(
    private val service: SponsorService
) : CommandHandler<FindSponsorsCommand, MutableList<Sponsor>> {
    override val commandType = FindSponsorsCommand::class

    override fun handle(command: FindSponsorsCommand): MutableList<Sponsor> {
        return service.findAll()
    }
}

@Component
class CreateSponsorHandler(
    private val service: SponsorService
) : CommandHandler<CreateSponsorCommand, Sponsor> {
    override val commandType = CreateSponsorCommand::class

    override fun handle(command: CreateSponsorCommand): Sponsor {
        var sponsor = Sponsor()
        sponsor.name = command.name
        sponsor.description = command.description
        sponsor = service.create(sponsor)
        return sponsor
    }
}

@Component
class UpdateSponsorHandler(
    private val service: SponsorService
) : CommandHandler<UpdateSponsorCommand, Sponsor> {
    override val commandType = UpdateSponsorCommand::class

    override fun handle(command: UpdateSponsorCommand): Sponsor {
        var sponsor = service.findById(command.id)
        sponsor.name = command.name
        sponsor.description = command.description
        command.version?.let { sponsor.version = it }
        sponsor = service.update(sponsor)
        return sponsor
    }
}

@Component
class FindSponsorByIdHandler(
    private val service: SponsorService
) : CommandHandler<FindSponsorByIdCommand, Sponsor> {
    override val commandType = FindSponsorByIdCommand::class

    override fun handle(command: FindSponsorByIdCommand): Sponsor {
        return service.findById(command.id)
    }
}

@Component
class DeleteSponsorByIdHandler(
    private val service: SponsorService
) : CommandHandler<DeleteSponsorByIdCommand, Unit> {
    override val commandType = DeleteSponsorByIdCommand::class

    override fun handle(command: DeleteSponsorByIdCommand) {
        service.deleteById(command.id)
    }
}

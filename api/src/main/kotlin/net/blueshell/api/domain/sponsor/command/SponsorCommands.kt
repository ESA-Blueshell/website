package net.blueshell.api.domain.sponsor.command

import net.blueshell.api.domain.sponsor.persistence.Sponsor
import net.blueshell.api.domain.sponsor.web.dto.SponsorDTO
import net.blueshell.api.shared.command.Command

class FindSponsorsCommand : Command<MutableList<Sponsor>>

data class CreateSponsorCommand(
    val dto: SponsorDTO
) : Command<Sponsor>

data class UpdateSponsorCommand(
    val id: Long,
    val dto: SponsorDTO
) : Command<Sponsor>

data class FindSponsorByIdCommand(
    val id: Long
) : Command<Sponsor>

data class DeleteSponsorByIdCommand(
    val id: Long
) : Command<Unit>

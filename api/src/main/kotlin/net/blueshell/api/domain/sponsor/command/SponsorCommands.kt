package net.blueshell.api.domain.sponsor.command

import net.blueshell.api.domain.sponsor.persistence.Sponsor
import net.blueshell.api.shared.command.Command

class FindSponsorsCommand : Command<MutableList<Sponsor>>

data class CreateSponsorCommand(
    val name: String,
    val description: String
) : Command<Sponsor>

data class UpdateSponsorCommand(
    val id: Long,
    val name: String,
    val description: String,
    val version: Long?
) : Command<Sponsor>

data class FindSponsorByIdCommand(
    val id: Long
) : Command<Sponsor>

data class DeleteSponsorByIdCommand(
    val id: Long
) : Command<Unit>

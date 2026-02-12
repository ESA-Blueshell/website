package net.blueshell.api.domain.sponsor.web.mapping

import net.blueshell.api.domain.sponsor.command.CreateSponsorCommand
import net.blueshell.api.domain.sponsor.command.UpdateSponsorCommand
import net.blueshell.api.domain.sponsor.web.dto.CreateSponsorRequest
import net.blueshell.api.domain.sponsor.web.dto.UpdateSponsorRequest
import tech.mappie.api.ObjectMappie

object CreateSponsorRequestToCommandMapper : ObjectMappie<CreateSponsorRequest, CreateSponsorCommand>() {
    override fun map(from: CreateSponsorRequest) = mapping {
        CreateSponsorCommand::name fromProperty { from.name!! }
        CreateSponsorCommand::description fromProperty { from.description!! }
    }
}

private data class UpdateSponsorCommandRequest(
    val id: Long,
    val request: UpdateSponsorRequest
)

object UpdateSponsorCommandRequestToCommandMapper : ObjectMappie<UpdateSponsorCommandRequest, UpdateSponsorCommand>() {
    override fun map(from: UpdateSponsorCommandRequest) = mapping {
        UpdateSponsorCommand::id fromProperty from::id
        UpdateSponsorCommand::name fromProperty { from.request.name!! }
        UpdateSponsorCommand::description fromProperty { from.request.description!! }
        UpdateSponsorCommand::version fromProperty { from.request.version }
    }
}

fun CreateSponsorRequest.asCommand(): CreateSponsorCommand = CreateSponsorRequestToCommandMapper.map(this)

fun UpdateSponsorRequest.asCommand(id: Long): UpdateSponsorCommand =
    UpdateSponsorCommandRequestToCommandMapper.map(UpdateSponsorCommandRequest(id, this))

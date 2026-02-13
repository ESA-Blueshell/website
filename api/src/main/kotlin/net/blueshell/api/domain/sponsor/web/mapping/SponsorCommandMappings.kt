package net.blueshell.api.domain.sponsor.web.mapping

import net.blueshell.api.domain.sponsor.command.CreateSponsorCommand
import net.blueshell.api.domain.sponsor.command.UpdateSponsorCommand
import net.blueshell.api.domain.sponsor.web.dto.request.CreateSponsorRequest
import net.blueshell.api.domain.sponsor.web.dto.request.UpdateSponsorRequest
import tech.mappie.api.ObjectMappie

object CreateSponsorRequestToCommandMapper : ObjectMappie<CreateSponsorRequest, CreateSponsorCommand>() {
    override fun map(from: CreateSponsorRequest) = mapping {
        CreateSponsorCommand::name fromValue from.name!!
        CreateSponsorCommand::description fromValue from.description!!
    }
}

internal data class UpdateSponsorCommandRequest(
    val id: Long,
    val request: UpdateSponsorRequest
)

internal object UpdateSponsorCommandRequestToCommandMapper : ObjectMappie<UpdateSponsorCommandRequest, UpdateSponsorCommand>() {
    override fun map(from: UpdateSponsorCommandRequest) = mapping {
        UpdateSponsorCommand::id fromProperty from::id
        UpdateSponsorCommand::name fromValue from.request.name!!
        UpdateSponsorCommand::description fromValue from.request.description!!
        UpdateSponsorCommand::version fromValue from.request.version
    }
}

fun CreateSponsorRequest.asCommand(): CreateSponsorCommand = CreateSponsorRequestToCommandMapper.map(this)

fun UpdateSponsorRequest.asCommand(id: Long): UpdateSponsorCommand =
    UpdateSponsorCommandRequestToCommandMapper.map(UpdateSponsorCommandRequest(id, this))

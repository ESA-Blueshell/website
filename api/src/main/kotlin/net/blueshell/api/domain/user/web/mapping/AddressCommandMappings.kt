package net.blueshell.api.domain.user.web.mapping

import net.blueshell.api.domain.user.command.CreateAddressCommand
import net.blueshell.api.domain.user.command.UpdateAddressCommand
import net.blueshell.api.domain.user.web.dto.CreateAddressRequest
import net.blueshell.api.domain.user.web.dto.UpdateAddressRequest
import tech.mappie.api.ObjectMappie

private data class CreateAddressCommandRequest(
    val userId: Long,
    val request: CreateAddressRequest
)

object CreateAddressCommandRequestToCommandMapper : ObjectMappie<CreateAddressCommandRequest, CreateAddressCommand>() {
    override fun map(from: CreateAddressCommandRequest) = mapping {
        CreateAddressCommand::userId fromProperty from::userId
        CreateAddressCommand::country fromProperty { from.request.country!! }
        CreateAddressCommand::city fromProperty { from.request.city!! }
        CreateAddressCommand::street fromProperty { from.request.street!! }
        CreateAddressCommand::houseNumber fromProperty { from.request.houseNumber!! }
        CreateAddressCommand::zipCode fromProperty { from.request.zipCode!! }
    }
}

private data class UpdateAddressCommandRequest(
    val id: Long,
    val request: UpdateAddressRequest
)

object UpdateAddressCommandRequestToCommandMapper : ObjectMappie<UpdateAddressCommandRequest, UpdateAddressCommand>() {
    override fun map(from: UpdateAddressCommandRequest) = mapping {
        UpdateAddressCommand::id fromProperty from::id
        UpdateAddressCommand::country fromProperty { from.request.country!! }
        UpdateAddressCommand::city fromProperty { from.request.city!! }
        UpdateAddressCommand::street fromProperty { from.request.street!! }
        UpdateAddressCommand::houseNumber fromProperty { from.request.houseNumber!! }
        UpdateAddressCommand::zipCode fromProperty { from.request.zipCode!! }
        UpdateAddressCommand::version fromProperty { from.request.version }
    }
}

fun CreateAddressRequest.asCommand(userId: Long): CreateAddressCommand =
    CreateAddressCommandRequestToCommandMapper.map(CreateAddressCommandRequest(userId, this))

fun UpdateAddressRequest.asCommand(id: Long): UpdateAddressCommand =
    UpdateAddressCommandRequestToCommandMapper.map(UpdateAddressCommandRequest(id, this))

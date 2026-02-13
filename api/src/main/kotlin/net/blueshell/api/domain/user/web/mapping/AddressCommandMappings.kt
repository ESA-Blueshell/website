package net.blueshell.api.domain.user.web.mapping

import net.blueshell.api.domain.user.command.CreateAddressCommand
import net.blueshell.api.domain.user.command.UpdateAddressCommand
import net.blueshell.api.domain.user.web.dto.CreateAddressRequest
import net.blueshell.api.domain.user.web.dto.UpdateAddressRequest
import tech.mappie.api.ObjectMappie

internal data class CreateAddressCommandRequest(
    val userId: Long,
    val request: CreateAddressRequest
)

internal object CreateAddressCommandRequestToCommandMapper : ObjectMappie<CreateAddressCommandRequest, CreateAddressCommand>() {
    override fun map(from: CreateAddressCommandRequest) = mapping {
        CreateAddressCommand::userId fromProperty from::userId
        CreateAddressCommand::country fromValue from.request.country!!
        CreateAddressCommand::city fromValue from.request.city!!
        CreateAddressCommand::street fromValue from.request.street!!
        CreateAddressCommand::houseNumber fromValue from.request.houseNumber!!
        CreateAddressCommand::zipCode fromValue from.request.zipCode!!
    }
}

internal data class UpdateAddressCommandRequest(
    val id: Long,
    val request: UpdateAddressRequest
)

internal object UpdateAddressCommandRequestToCommandMapper : ObjectMappie<UpdateAddressCommandRequest, UpdateAddressCommand>() {
    override fun map(from: UpdateAddressCommandRequest) = mapping {
        UpdateAddressCommand::id fromProperty from::id
        UpdateAddressCommand::country fromValue from.request.country!!
        UpdateAddressCommand::city fromValue from.request.city!!
        UpdateAddressCommand::street fromValue from.request.street!!
        UpdateAddressCommand::houseNumber fromValue from.request.houseNumber!!
        UpdateAddressCommand::zipCode fromValue from.request.zipCode!!
        UpdateAddressCommand::version fromValue from.request.version
    }
}

fun CreateAddressRequest.asCommand(userId: Long): CreateAddressCommand =
    CreateAddressCommandRequestToCommandMapper.map(CreateAddressCommandRequest(userId, this))

fun UpdateAddressRequest.asCommand(id: Long): UpdateAddressCommand =
    UpdateAddressCommandRequestToCommandMapper.map(UpdateAddressCommandRequest(id, this))

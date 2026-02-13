package net.blueshell.api.domain.user.web.mapping

import net.blueshell.api.domain.user.command.*
import net.blueshell.api.domain.user.web.dto.CreateGuestUserRequest
import net.blueshell.api.domain.user.web.dto.CreateUserRequest
import net.blueshell.api.domain.user.web.dto.UpdateGuestUserRequest
import net.blueshell.api.domain.user.web.dto.UpdateUserRequest
import net.blueshell.api.shared.enums.Role
import tech.mappie.api.ObjectMappie

internal data class CreateUserCommandRequest(
    val isBoard: Boolean,
    val request: CreateUserRequest
)

internal object CreateUserCommandRequestToCommandMapper : ObjectMappie<CreateUserCommandRequest, CreateUserCommand>() {
    override fun map(from: CreateUserCommandRequest) = mapping {
        CreateUserCommand::isBoard fromValue from.isBoard
        CreateUserCommand::roles fromValue { from.request.roles ?: emptySet<Role>() }
        CreateUserCommand::dateOfBirth fromValue from.request.dateOfBirth
        CreateUserCommand::nationality fromValue from.request.nationality
        CreateUserCommand::photoConsent fromValue from.request.photoConsent!!
        CreateUserCommand::ehbo fromValue from.request.ehbo!!
        CreateUserCommand::bhv fromValue from.request.bhv!!
        CreateUserCommand::enabled fromValue { from.request.enabled ?: false }
        CreateUserCommand::gender fromValue from.request.gender
        CreateUserCommand::studentNumber fromValue from.request.studentNumber
        CreateUserCommand::username fromValue from.request.username
        CreateUserCommand::email fromValue from.request.email
        CreateUserCommand::initials fromValue from.request.initials
        CreateUserCommand::firstName fromValue from.request.firstName
        CreateUserCommand::prefix fromValue from.request.prefix
        CreateUserCommand::lastName fromValue from.request.lastName
        CreateUserCommand::newsletter fromValue from.request.newsletter!!
        CreateUserCommand::password fromValue from.request.password
        CreateUserCommand::addressId fromValue from.request.addressId
        CreateUserCommand::discord fromValue from.request.discord
        CreateUserCommand::phoneNumber fromValue from.request.phoneNumber
    }
}

internal data class CreateGuestUserCommandRequest(
    val request: CreateGuestUserRequest
)

internal object CreateGuestUserCommandRequestToCommandMapper : ObjectMappie<CreateGuestUserCommandRequest, CreateGuestUserCommand>() {
    override fun map(from: CreateGuestUserCommandRequest) = mapping {
        CreateGuestUserCommand::username fromValue from.request.username
        CreateGuestUserCommand::initials fromValue from.request.initials
        CreateGuestUserCommand::firstName fromValue from.request.firstName
        CreateGuestUserCommand::prefix fromValue from.request.prefix
        CreateGuestUserCommand::lastName fromValue from.request.lastName
        CreateGuestUserCommand::newsletter fromValue from.request.newsletter!!
        CreateGuestUserCommand::password fromValue from.request.password!!
        CreateGuestUserCommand::addressId fromValue from.request.addressId
        CreateGuestUserCommand::email fromValue from.request.email
        CreateGuestUserCommand::discord fromValue from.request.discord
        CreateGuestUserCommand::phoneNumber fromValue from.request.phoneNumber
    }
}

internal data class UpdateGuestUserCommandRequest(
    val id: Long,
    val request: UpdateGuestUserRequest
)

internal object UpdateGuestUserCommandRequestToCommandMapper : ObjectMappie<UpdateGuestUserCommandRequest, UpdateGuestUserCommand>() {
    override fun map(from: UpdateGuestUserCommandRequest) = mapping {
        UpdateGuestUserCommand::id fromProperty from::id
        UpdateGuestUserCommand::discord fromValue from.request.discord
        UpdateGuestUserCommand::phoneNumber fromValue from.request.phoneNumber
        UpdateGuestUserCommand::newsletter fromValue from.request.newsletter!!
        UpdateGuestUserCommand::version fromValue from.request.version
    }
}

internal data class UpdateUserCommandRequest(
    val id: Long,
    val isBoard: Boolean,
    val request: UpdateUserRequest
)

internal object UpdateUserCommandRequestToCommandMapper : ObjectMappie<UpdateUserCommandRequest, UpdateUserCommand>() {
    override fun map(from: UpdateUserCommandRequest) = mapping {
        UpdateUserCommand::id fromValue from.id
        UpdateUserCommand::isBoard fromValue from.isBoard
        UpdateUserCommand::roles fromValue { from.request.roles ?: emptySet<Role>() }
        UpdateUserCommand::dateOfBirth fromValue from.request.dateOfBirth
        UpdateUserCommand::nationality fromValue from.request.nationality
        UpdateUserCommand::photoConsent fromValue from.request.photoConsent!!
        UpdateUserCommand::ehbo fromValue from.request.ehbo!!
        UpdateUserCommand::bhv fromValue from.request.bhv!!
        UpdateUserCommand::enabled fromValue { from.request.enabled ?: false }
        UpdateUserCommand::gender fromValue from.request.gender
        UpdateUserCommand::studentNumber fromValue from.request.studentNumber
        UpdateUserCommand::username fromValue from.request.username
        UpdateUserCommand::email fromValue from.request.email
        UpdateUserCommand::initials fromValue from.request.initials
        UpdateUserCommand::firstName fromValue from.request.firstName
        UpdateUserCommand::prefix fromProperty from.request::prefix
        UpdateUserCommand::lastName fromProperty from.request::lastName
        UpdateUserCommand::newsletter fromValue from.request.newsletter!!
        UpdateUserCommand::addressId fromProperty from.request::addressId
        UpdateUserCommand::discord fromProperty from.request::discord
        UpdateUserCommand::phoneNumber fromProperty from.request::phoneNumber
        UpdateUserCommand::version fromProperty from.request::version
    }
}

fun CreateUserRequest.asCommand(isBoard: Boolean): CreateUserCommand =
    CreateUserCommandRequestToCommandMapper.map(CreateUserCommandRequest(isBoard, this))

fun CreateGuestUserRequest.asCommand(): CreateGuestUserCommand =
    CreateGuestUserCommandRequestToCommandMapper.map(CreateGuestUserCommandRequest(this))

fun UpdateGuestUserRequest.asCommand(id: Long): UpdateGuestUserCommand =
    UpdateGuestUserCommandRequestToCommandMapper.map(UpdateGuestUserCommandRequest(id, this))

fun UpdateUserRequest.asCommand(id: Long, isBoard: Boolean): UpdateUserCommand =
    UpdateUserCommandRequestToCommandMapper.map(UpdateUserCommandRequest(id, isBoard, this))

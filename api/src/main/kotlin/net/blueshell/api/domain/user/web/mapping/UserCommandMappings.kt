package net.blueshell.api.domain.user.web.mapping

import net.blueshell.api.domain.user.command.*
import net.blueshell.api.domain.user.web.dto.CreateGuestUserRequest
import net.blueshell.api.domain.user.web.dto.CreateUserRequest
import net.blueshell.api.domain.user.web.dto.UpdateGuestUserRequest
import net.blueshell.api.domain.user.web.dto.UpdateUserRequest
import net.blueshell.api.shared.enums.Role
import tech.mappie.api.ObjectMappie

private data class CreateUserCommandRequest(
    val isBoard: Boolean,
    val request: CreateUserRequest
)

object CreateUserCommandRequestToCommandMapper : ObjectMappie<CreateUserCommandRequest, CreateUserCommand>() {
    override fun map(from: CreateUserCommandRequest) = mapping {
        CreateUserCommand::isBoard fromProperty { from.isBoard }
        CreateUserCommand::roles fromProperty { from.request.roles ?: emptySet<Role>() }
        CreateUserCommand::dateOfBirth fromProperty { from.request.dateOfBirth }
        CreateUserCommand::nationality fromProperty { from.request.nationality }
        CreateUserCommand::photoConsent fromProperty { from.request.photoConsent!! }
        CreateUserCommand::ehbo fromProperty { from.request.ehbo!! }
        CreateUserCommand::bhv fromProperty { from.request.bhv!! }
        CreateUserCommand::enabled fromProperty { from.request.enabled ?: false }
        CreateUserCommand::gender fromProperty { from.request.gender }
        CreateUserCommand::studentNumber fromProperty { from.request.studentNumber }
        CreateUserCommand::username fromProperty { from.request.username }
        CreateUserCommand::email fromProperty { from.request.email }
        CreateUserCommand::initials fromProperty { from.request.initials }
        CreateUserCommand::firstName fromProperty { from.request.firstName }
        CreateUserCommand::prefix fromProperty { from.request.prefix }
        CreateUserCommand::lastName fromProperty { from.request.lastName }
        CreateUserCommand::newsletter fromProperty { from.request.newsletter!! }
        CreateUserCommand::password fromProperty { from.request.password }
        CreateUserCommand::addressId fromProperty { from.request.addressId }
        CreateUserCommand::discord fromProperty { from.request.discord }
        CreateUserCommand::phoneNumber fromProperty { from.request.phoneNumber }
    }
}

private data class CreateGuestUserCommandRequest(
    val request: CreateGuestUserRequest
)

object CreateGuestUserCommandRequestToCommandMapper : ObjectMappie<CreateGuestUserCommandRequest, CreateGuestUserCommand>() {
    override fun map(from: CreateGuestUserCommandRequest) = mapping {
        CreateGuestUserCommand::username fromProperty { from.request.username }
        CreateGuestUserCommand::initials fromProperty { from.request.initials }
        CreateGuestUserCommand::firstName fromProperty { from.request.firstName }
        CreateGuestUserCommand::prefix fromProperty { from.request.prefix }
        CreateGuestUserCommand::lastName fromProperty { from.request.lastName }
        CreateGuestUserCommand::newsletter fromProperty { from.request.newsletter!! }
        CreateGuestUserCommand::password fromProperty { from.request.password!! }
        CreateGuestUserCommand::addressId fromProperty { from.request.addressId }
        CreateGuestUserCommand::email fromProperty { from.request.email }
        CreateGuestUserCommand::discord fromProperty { from.request.discord }
        CreateGuestUserCommand::phoneNumber fromProperty { from.request.phoneNumber }
    }
}

private data class UpdateGuestUserCommandRequest(
    val id: Long,
    val request: UpdateGuestUserRequest
)

object UpdateGuestUserCommandRequestToCommandMapper : ObjectMappie<UpdateGuestUserCommandRequest, UpdateGuestUserCommand>() {
    override fun map(from: UpdateGuestUserCommandRequest) = mapping {
        UpdateGuestUserCommand::id fromProperty from::id
        UpdateGuestUserCommand::discord fromProperty { from.request.discord }
        UpdateGuestUserCommand::phoneNumber fromProperty { from.request.phoneNumber }
        UpdateGuestUserCommand::newsletter fromProperty { from.request.newsletter!! }
        UpdateGuestUserCommand::version fromProperty { from.request.version }
    }
}

private data class UpdateUserCommandRequest(
    val id: Long,
    val isBoard: Boolean,
    val request: UpdateUserRequest
)

object UpdateUserCommandRequestToCommandMapper : ObjectMappie<UpdateUserCommandRequest, UpdateUserCommand>() {
    override fun map(from: UpdateUserCommandRequest) = mapping {
        UpdateUserCommand::id fromProperty { from.id }
        UpdateUserCommand::isBoard fromProperty { from.isBoard }
        UpdateUserCommand::roles fromProperty { from.request.roles ?: emptySet<Role>() }
        UpdateUserCommand::dateOfBirth fromProperty { from.request.dateOfBirth }
        UpdateUserCommand::nationality fromProperty { from.request.nationality }
        UpdateUserCommand::photoConsent fromProperty { from.request.photoConsent!! }
        UpdateUserCommand::ehbo fromProperty { from.request.ehbo!! }
        UpdateUserCommand::bhv fromProperty { from.request.bhv!! }
        UpdateUserCommand::enabled fromProperty { from.request.enabled ?: false }
        UpdateUserCommand::gender fromProperty { from.request.gender }
        UpdateUserCommand::studentNumber fromProperty { from.request.studentNumber }
        UpdateUserCommand::username fromProperty { from.request.username }
        UpdateUserCommand::email fromProperty { from.request.email }
        UpdateUserCommand::initials fromProperty { from.request.initials }
        UpdateUserCommand::firstName fromProperty { from.request.firstName }
        UpdateUserCommand::prefix fromProperty { from.request.prefix }
        UpdateUserCommand::lastName fromProperty { from.request.lastName }
        UpdateUserCommand::newsletter fromProperty { from.request.newsletter!! }
        UpdateUserCommand::addressId fromProperty { from.request.addressId }
        UpdateUserCommand::discord fromProperty { from.request.discord }
        UpdateUserCommand::phoneNumber fromProperty { from.request.phoneNumber }
        UpdateUserCommand::version fromProperty { from.request.version }
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

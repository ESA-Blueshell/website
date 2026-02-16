package net.blueshell.api.domain.user.web.mapping

import net.blueshell.api.domain.user.command.*
import net.blueshell.api.domain.user.web.dto.*
import tech.mappie.api.ObjectMappie

internal data class CreateUserCommandRequest(
    val isBoard: Boolean,
    val request: CreateUserRequest
)

internal object CreateUserCommandRequestToCommandMapper : ObjectMappie<CreateUserCommandRequest, CreateUserCommand>() {
    override fun map(from: CreateUserCommandRequest) = mapping {
        CreateUserCommand::isBoard fromValue from.isBoard
        CreateUserCommand::photoConsent fromValue (from.request.photoConsent ?: false)
        CreateUserCommand::username fromValue from.request.username
        CreateUserCommand::email fromValue from.request.email
        CreateUserCommand::initials fromValue from.request.initials
        CreateUserCommand::firstName fromValue from.request.firstName
        CreateUserCommand::prefix fromValue from.request.prefix
        CreateUserCommand::lastName fromValue from.request.lastName
        CreateUserCommand::newsletter fromValue (from.request.newsletter ?: false)
        CreateUserCommand::password fromValue from.request.password
        CreateUserCommand::discord fromValue from.request.discord
        CreateUserCommand::phoneNumber fromValue from.request.phoneNumber
    }
}

internal data class CreateGuestUserCommandRequest(
    val request: CreateGuestUserRequest
)

internal object CreateGuestUserCommandRequestToCommandMapper :
    ObjectMappie<CreateGuestUserCommandRequest, CreateGuestUserCommand>() {
    override fun map(from: CreateGuestUserCommandRequest) = mapping {
        CreateGuestUserCommand::username fromValue (from.request.username ?: "")
        CreateGuestUserCommand::initials fromValue (from.request.initials ?: "")
        CreateGuestUserCommand::firstName fromValue (from.request.firstName ?: "")
        CreateGuestUserCommand::prefix fromValue from.request.prefix
        CreateGuestUserCommand::lastName fromValue (from.request.lastName ?: "")
        CreateGuestUserCommand::newsletter fromValue (from.request.newsletter ?: false)
        CreateGuestUserCommand::password fromValue (from.request.password ?: "")
        CreateGuestUserCommand::email fromValue from.request.email
        CreateGuestUserCommand::discord fromValue from.request.discord
        CreateGuestUserCommand::phoneNumber fromValue from.request.phoneNumber
    }
}

internal data class UpdateGuestUserCommandRequest(
    val id: Long,
    val request: UpdateGuestUserRequest
)

internal object UpdateGuestUserCommandRequestToCommandMapper :
    ObjectMappie<UpdateGuestUserCommandRequest, UpdateGuestUserCommand>() {
    override fun map(from: UpdateGuestUserCommandRequest) = mapping {
        UpdateGuestUserCommand::id fromProperty from::id
        UpdateGuestUserCommand::discord fromValue from.request.discord
        UpdateGuestUserCommand::phoneNumber fromValue from.request.phoneNumber
        UpdateGuestUserCommand::newsletter fromValue (from.request.newsletter ?: false)
        UpdateGuestUserCommand::version fromValue from.request.version!!
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
        UpdateUserCommand::username fromValue from.request.username
        UpdateUserCommand::email fromValue from.request.email
        UpdateUserCommand::initials fromValue from.request.initials
        UpdateUserCommand::firstName fromValue from.request.firstName
        UpdateUserCommand::prefix fromProperty from.request::prefix
        UpdateUserCommand::lastName fromProperty from.request::lastName
        UpdateUserCommand::newsletter fromValue (from.request.newsletter ?: false)
        UpdateUserCommand::discord fromProperty from.request::discord
        UpdateUserCommand::phoneNumber fromProperty from.request::phoneNumber
        UpdateUserCommand::version fromValue from.request.version!!
    }
}

internal object UserStudyRequestToCommandDataMapper : ObjectMappie<UserStudyRequest, UserStudyData>() {
    override fun map(from: UserStudyRequest) = mapping {
        UserStudyData::level fromValue from.level!!
        UserStudyData::programName fromValue from.programName!!.trim()
        UserStudyData::status fromValue from.status!!
        UserStudyData::startYear fromValue from.startYear
        UserStudyData::graduationYear fromValue from.graduationYear
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

fun UserStudyRequest.asData(): UserStudyData = UserStudyRequestToCommandDataMapper.map(this)
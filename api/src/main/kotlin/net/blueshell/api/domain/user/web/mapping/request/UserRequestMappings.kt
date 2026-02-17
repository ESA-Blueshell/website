package net.blueshell.api.domain.user.web.mapping.request

import net.blueshell.api.domain.user.command.*
import net.blueshell.api.domain.user.web.dto.request.BoardUpdateUserRequest
import net.blueshell.api.domain.user.web.dto.request.CreateUserRequest
import net.blueshell.api.domain.user.web.dto.request.UpdateUserRequest
import tech.mappie.api.ObjectMappie

internal object CreateUserRequestToCommandMapper : ObjectMappie<CreateUserRequest, CreateUserCommand>() {
    fun map(from: CreateUserRequest, isBoard: Boolean) = mapping {
        CreateUserCommand::isBoard fromValue isBoard
        CreateUserCommand::username fromValue from.username
        CreateUserCommand::initials fromValue from.initials
        CreateUserCommand::firstName fromValue from.firstName
        CreateUserCommand::prefix fromValue from.prefix
        CreateUserCommand::lastName fromValue from.lastName
        CreateUserCommand::newsletter fromValue from.newsletter
        CreateUserCommand::password fromValue from.password
        CreateUserCommand::email fromValue from.email
        CreateUserCommand::discord fromValue from.discord
        CreateUserCommand::phoneNumber fromValue from.phoneNumber
    }
}

fun CreateUserRequest.asCommand(isBoard: Boolean): CreateUserCommand =
    CreateUserRequestToCommandMapper.map(this, isBoard)

internal object BoardUpdateUserRequestToCommandMapper : ObjectMappie<BoardUpdateUserRequest, BoardUpdateUserCommand>() {
    fun map(from: BoardUpdateUserRequest, id: Long) = mapping {
        BoardUpdateUserCommand::id fromValue id
        BoardUpdateUserCommand::username fromValue from.username
        BoardUpdateUserCommand::initials fromValue from.initials
        BoardUpdateUserCommand::firstName fromValue from.firstName
        BoardUpdateUserCommand::prefix fromValue from.prefix
        BoardUpdateUserCommand::lastName fromValue from.lastName
        BoardUpdateUserCommand::newsletter fromValue from.newsletter
        BoardUpdateUserCommand::email fromValue from.email
        BoardUpdateUserCommand::discord fromValue from.discord
        BoardUpdateUserCommand::phoneNumber fromValue from.phoneNumber
    }
}

fun BoardUpdateUserRequest.asCommand(id: Long): BoardUpdateUserCommand =
    BoardUpdateUserRequestToCommandMapper.map(this, id)

internal object UpdateUserRequestToCommandMapper :
    ObjectMappie<UpdateUserRequest, UpdateUserCommand>() {
    fun map(from: UpdateUserRequest, id: Long) = mapping {
        UpdateUserCommand::id fromValue id
        UpdateUserCommand::discord fromProperty from::discord
        UpdateUserCommand::phoneNumber fromProperty from::phoneNumber
        UpdateUserCommand::newsletter fromProperty from::newsletter
        UpdateUserCommand::version fromValue from.version!!
    }
}

fun UpdateUserRequest.asCommand(id: Long): UpdateUserCommand =
    UpdateUserRequestToCommandMapper.map(this, id)

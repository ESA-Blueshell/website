package net.blueshell.api.domain.user.web.mapping.request

import net.blueshell.api.domain.user.command.BoardUpdateUserCommand
import net.blueshell.api.domain.user.command.CreateUserCommand
import net.blueshell.api.domain.user.command.UpdateUserCommand
import net.blueshell.api.domain.user.web.dto.request.BoardUpdateUserRequest
import net.blueshell.api.domain.user.web.dto.request.CreateUserRequest
import net.blueshell.api.domain.user.web.dto.request.UpdateUserRequest

fun CreateUserRequest.asCommand(isBoard: Boolean): CreateUserCommand =
    CreateUserCommand(
        isBoard = isBoard,
        username = this.username!!,
        initials = this.initials!!,
        firstName = this.firstName!!,
        prefix = this.prefix,
        lastName = this.lastName!!,
        newsletter = this.newsletter!!,
        password = this.password!!,
        email = this.email!!,
        discord = this.discord!!,
        phoneNumber = this.phoneNumber!!
    )

fun BoardUpdateUserRequest.asCommand(id: Long): BoardUpdateUserCommand =
    BoardUpdateUserCommand(
        id = id,
        username = this.username!!,
        initials = this.initials!!,
        firstName = this.firstName!!,
        prefix = this.prefix,
        lastName = this.lastName!!,
        newsletter = this.newsletter!!,
        email = this.email!!,
        discord = this.discord!!,
        phoneNumber = this.phoneNumber!!,
        version = this.version!!
    )

fun UpdateUserRequest.asCommand(id: Long): UpdateUserCommand =
    UpdateUserCommand(
        id = id,
        discord = this.discord!!,
        phoneNumber = this.phoneNumber!!,
        newsletter = this.newsletter!!,
        version = this.version!!
    )

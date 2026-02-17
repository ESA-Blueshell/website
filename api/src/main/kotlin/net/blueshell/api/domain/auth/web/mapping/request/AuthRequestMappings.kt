package net.blueshell.api.domain.auth.web.mapping.request

import net.blueshell.api.domain.auth.command.AuthenticateCommand
import net.blueshell.api.domain.auth.command.MemberActivateCommand
import net.blueshell.api.domain.auth.command.SetPasswordCommand
import net.blueshell.api.domain.auth.command.UserActivateCommand
import net.blueshell.api.domain.auth.web.dto.request.JwtRequest
import net.blueshell.api.domain.auth.web.dto.request.MemberActivationRequest
import net.blueshell.api.domain.auth.web.dto.request.PasswordResetRequest
import net.blueshell.api.domain.auth.web.dto.request.UserActivationRequest

fun JwtRequest.asCommand(): AuthenticateCommand =
    AuthenticateCommand(
        username = this.username!!,
        password = this.password!!,
    )

fun PasswordResetRequest.asCommand(): SetPasswordCommand =
    SetPasswordCommand(
        token = this.token!!,
        password = this.password!!,
    )

fun UserActivationRequest.asCommand(): UserActivateCommand =
    UserActivateCommand(
        token = this.token!!,
    )

fun MemberActivationRequest.asCommand(): MemberActivateCommand =
    MemberActivateCommand(
        token = this.token!!,
        username = this.username!!,
        password = this.password!!,
    )

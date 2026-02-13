package net.blueshell.api.domain.auth.web.mapping

import net.blueshell.api.domain.auth.command.*
import net.blueshell.api.domain.auth.domain.model.AuthenticationSession
import net.blueshell.api.domain.auth.web.dto.request.JwtRequest
import net.blueshell.api.domain.auth.web.dto.request.MemberActivationRequest
import net.blueshell.api.domain.auth.web.dto.request.PasswordResetRequest
import net.blueshell.api.domain.auth.web.dto.request.UserActivationRequest
import net.blueshell.api.domain.auth.web.dto.response.AuthenticationResponse
import tech.mappie.api.ObjectMappie

object JwtRequestToCommandMapper : ObjectMappie<JwtRequest, AuthenticateCommand>() {
    override fun map(from: JwtRequest) = mapping {
        AuthenticateCommand::username fromValue from.username!!
        AuthenticateCommand::password fromValue from.password!!
    }
}

object PasswordResetRequestToCommandMapper : ObjectMappie<PasswordResetRequest, SetPasswordCommand>() {
    override fun map(from: PasswordResetRequest) = mapping {
        SetPasswordCommand::token fromValue from.token!!
        SetPasswordCommand::password fromValue from.password!!
    }
}

object UserActivationRequestToCommandMapper : ObjectMappie<UserActivationRequest, UserActivateCommand>() {
    override fun map(from: UserActivationRequest) = mapping {
        UserActivateCommand::token fromValue from.token!!
    }
}

object MemberActivationRequestToCommandMapper : ObjectMappie<MemberActivationRequest, MemberActivateCommand>() {
    override fun map(from: MemberActivationRequest) = mapping {
        MemberActivateCommand::token fromValue from.token!!
        MemberActivateCommand::username fromValue from.username!!
        MemberActivateCommand::password fromValue from.password!!
    }
}

fun JwtRequest.asCommand(): AuthenticateCommand = JwtRequestToCommandMapper.map(this)

fun PasswordResetRequest.asCommand(): SetPasswordCommand = PasswordResetRequestToCommandMapper.map(this)

fun UserActivationRequest.asCommand(): UserActivateCommand = UserActivationRequestToCommandMapper.map(this)

fun MemberActivationRequest.asCommand(): MemberActivateCommand = MemberActivationRequestToCommandMapper.map(this)

fun AuthenticationSession.asResponse(): AuthenticationResponse {
    return AuthenticationResponse(
        token = token,
        userId = userId,
        username = username,
        expiration = expiresAtEpochMs,
        roles = roles.toMutableSet(),
        addressId = addressId
    )
}

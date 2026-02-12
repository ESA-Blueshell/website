package net.blueshell.api.domain.auth.web.mapping

import net.blueshell.api.domain.auth.command.*
import net.blueshell.api.domain.auth.web.dto.request.JwtRequest
import net.blueshell.api.domain.auth.web.dto.recovery.MemberActivationRequest
import net.blueshell.api.domain.auth.web.dto.recovery.PasswordResetRequest
import net.blueshell.api.domain.auth.web.dto.recovery.UserActivationRequest
import tech.mappie.api.ObjectMappie

object JwtRequestToCommandMapper : ObjectMappie<JwtRequest, AuthenticateCommand>() {
    override fun map(from: JwtRequest) = mapping {
        AuthenticateCommand::request fromProperty { from }
    }
}

object PasswordResetRequestToCommandMapper : ObjectMappie<PasswordResetRequest, SetPasswordCommand>() {
    override fun map(from: PasswordResetRequest) = mapping {
        SetPasswordCommand::token fromProperty { from.token!! }
        SetPasswordCommand::password fromProperty { from.password!! }
    }
}

object UserActivationRequestToCommandMapper : ObjectMappie<UserActivationRequest, UserActivateCommand>() {
    override fun map(from: UserActivationRequest) = mapping {
        UserActivateCommand::token fromProperty { from.token!! }
    }
}

object MemberActivationRequestToCommandMapper : ObjectMappie<MemberActivationRequest, MemberActivateCommand>() {
    override fun map(from: MemberActivationRequest) = mapping {
        MemberActivateCommand::token fromProperty { from.token!! }
        MemberActivateCommand::username fromProperty { from.username!! }
        MemberActivateCommand::password fromProperty { from.password!! }
    }
}

fun JwtRequest.asCommand(): AuthenticateCommand = JwtRequestToCommandMapper.map(this)

fun PasswordResetRequest.asCommand(): SetPasswordCommand = PasswordResetRequestToCommandMapper.map(this)

fun UserActivationRequest.asCommand(): UserActivateCommand = UserActivationRequestToCommandMapper.map(this)

fun MemberActivationRequest.asCommand(): MemberActivateCommand = MemberActivationRequestToCommandMapper.map(this)

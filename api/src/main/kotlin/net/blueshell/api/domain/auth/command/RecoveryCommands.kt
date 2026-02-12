package net.blueshell.api.domain.auth.command

import net.blueshell.api.domain.user.application.validation.UniqueUsername
import net.blueshell.api.domain.user.persistence.User
import net.blueshell.api.shared.command.Command

data class ResetPasswordCommand(
    val username: String
) : Command<Unit>

data class SetPasswordCommand(
    val token: String,
    val password: String
) : Command<Unit>

data class UserActivateCommand(
    val token: String
) : Command<User>

data class MemberActivateCommand(
    val token: String,
    @field:UniqueUsername
    val username: String,
    val password: String
) : Command<Unit>

data class ResendUserActivationCommand(
    val username: String
) : Command<Unit>

data class ResendMemberActivationEmailCommand(
    val userId: Long
) : Command<Unit>

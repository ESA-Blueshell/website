package net.blueshell.api.domain.auth.command

import jakarta.validation.constraints.NotBlank
import net.blueshell.api.domain.user.application.validation.UniqueUsername
import net.blueshell.api.domain.user.persistence.User
import net.blueshell.api.shared.command.Command

data class ResetPasswordCommand(
    @field:NotBlank
    val username: String
) : Command<Unit>

data class SetPasswordCommand(
    @field:NotBlank
    val token: String,
    @field:NotBlank
    val password: String
) : Command<Unit>

data class UserActivateCommand(
    @field:NotBlank
    val token: String
) : Command<User>

data class MemberActivateCommand(
    @field:NotBlank
    val token: String,
    @field:NotBlank
    @field:UniqueUsername
    val username: String,
    @field:NotBlank
    val password: String
) : Command<Unit>

data class ResendUserActivationCommand(
    @field:NotBlank
    val username: String
) : Command<Unit>

data class ResendMemberActivationEmailCommand(
    val userId: Long
) : Command<Unit>

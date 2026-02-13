package net.blueshell.api.domain.auth.command

import net.blueshell.api.domain.user.application.validation.UniqueUsername
import net.blueshell.api.domain.user.persistence.User
import net.blueshell.api.shared.command.Command
import jakarta.validation.constraints.NotBlank
import jakarta.validation.constraints.Pattern
import jakarta.validation.constraints.Size

data class ResetPasswordCommand(
    @field:NotBlank(message = "Username is required")
    val username: String
) : Command<Unit>

data class SetPasswordCommand(
    @field:NotBlank(message = "Token is required")
    val token: String,

    @field:NotBlank(message = "Password is required")
    @field:Size(min = 8, max = 100, message = "Password must be between 8 and 100 characters")
    @field:Pattern(
        regexp = "^(?=.*[a-z])(?=.*[A-Z])(?=.*\\d)(?=.*[@$!%*?&])[A-Za-z\\d@$!%*?&]+$",
        message = "Password must contain at least one lowercase letter, one uppercase letter, one digit, and one special character (@$!%*?&)"
    )
    val password: String
) : Command<Unit>

data class UserActivateCommand(
    @field:NotBlank(message = "Token is required")
    val token: String
) : Command<User>

data class MemberActivateCommand(
    @field:NotBlank(message = "Token is required")
    val token: String,

    @field:NotBlank(message = "Username is required")
    @field:UniqueUsername
    val username: String,

    @field:NotBlank(message = "Password is required")
    @field:Size(min = 8, max = 100, message = "Password must be between 8 and 100 characters")
    @field:Pattern(
        regexp = "^(?=.*[a-z])(?=.*[A-Z])(?=.*\\d)(?=.*[@$!%*?&])[A-Za-z\\d@$!%*?&]+$",
        message = "Password must contain at least one lowercase letter, one uppercase letter, one digit, and one special character (@$!%*?&)"
    )
    val password: String
) : Command<Unit>

data class ResendUserActivationCommand(
    @field:NotBlank(message = "Username is required")
    val username: String
) : Command<Unit>

data class ResendMemberActivationEmailCommand(
    val userId: Long
) : Command<Unit>

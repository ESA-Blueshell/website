package net.blueshell.api.domain.auth.command

import net.blueshell.api.domain.auth.application.AuthResult
import net.blueshell.api.shared.command.Command
import jakarta.validation.constraints.NotBlank

data class AuthenticateCommand(
    @field:NotBlank
    val username: String,
    @field:NotBlank
    val password: String
) : Command<AuthResult>

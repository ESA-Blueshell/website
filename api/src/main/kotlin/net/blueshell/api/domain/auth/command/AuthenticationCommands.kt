package net.blueshell.api.domain.auth.command

import net.blueshell.api.domain.auth.web.dto.request.JwtRequest
import net.blueshell.api.domain.auth.web.dto.response.AuthenticationDTO
import net.blueshell.api.shared.command.Command

data class AuthenticateCommand(
    val request: JwtRequest
) : Command<AuthenticationDTO>

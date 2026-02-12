package net.blueshell.api.domain.auth.web

import io.swagger.v3.oas.annotations.tags.Tag
import jakarta.annotation.security.PermitAll
import net.blueshell.api.domain.auth.command.AuthenticateCommand
import net.blueshell.api.domain.auth.security.JWTAuthBase
import net.blueshell.api.domain.auth.web.dto.request.JwtRequest
import net.blueshell.api.domain.auth.web.dto.response.AuthenticationResponse
import net.blueshell.api.shared.command.CommandBus
import org.springframework.validation.annotation.Validated
import org.springframework.web.bind.annotation.PostMapping
import org.springframework.web.bind.annotation.RequestBody
import org.springframework.web.bind.annotation.RestController

@RestController
@Tag(name = "Authentication")
class AuthenticationController(
    private val commandBus: CommandBus
) : JWTAuthBase() {

    @PostMapping(("/auth"))
    @PermitAll
    fun authenticate(@Validated @RequestBody authenticationRequest: JwtRequest): AuthenticationResponse {
        return commandBus.dispatch(AuthenticateCommand(authenticationRequest))
    }
}

package net.blueshell.api.domain.auth.web

import io.swagger.v3.oas.annotations.tags.Tag
import jakarta.annotation.security.PermitAll
import jakarta.servlet.http.HttpServletRequest
import jakarta.servlet.http.HttpServletResponse
import net.blueshell.api.infrastructure.security.AuthTokenCookieService
import net.blueshell.api.infrastructure.security.JwtRevocationService
import net.blueshell.api.infrastructure.security.JwtTokenUtil
import net.blueshell.api.domain.auth.web.dto.request.JwtRequest
import net.blueshell.api.domain.auth.web.dto.response.AuthenticationResponse
import net.blueshell.api.domain.auth.web.mapping.request.asCommand
import net.blueshell.api.domain.auth.web.mapping.response.asResponse
import net.blueshell.api.shared.command.CommandBus
import org.springframework.http.HttpStatus
import org.springframework.validation.annotation.Validated
import org.springframework.web.bind.annotation.PostMapping
import org.springframework.web.bind.annotation.RequestBody
import org.springframework.web.bind.annotation.ResponseStatus
import org.springframework.web.bind.annotation.RestController

@RestController
@Tag(name = "Authentication")
class AuthenticationController(
    private val commandBus: CommandBus,
    private val authTokenCookieService: AuthTokenCookieService,
    private val jwtTokenUtil: JwtTokenUtil,
    private val jwtRevocationService: JwtRevocationService
) {

    @PostMapping(("/auth"))
    @PermitAll
    fun authenticate(
        @Validated @RequestBody authenticationRequest: JwtRequest,
        response: HttpServletResponse
    ): AuthenticationResponse {
        val result = commandBus.dispatch(authenticationRequest.asCommand())
        authTokenCookieService.writeAuthCookie(response, result.token, result.expiresAtEpochMs - System.currentTimeMillis())
        return result.asResponse()
    }

    @PostMapping("/auth/logout")
    @PermitAll
    @ResponseStatus(HttpStatus.NO_CONTENT)
    fun logout(request: HttpServletRequest, response: HttpServletResponse) {
        resolveToken(request)?.let { token ->
            val validation = jwtTokenUtil.parseAndValidate(token)
            validation.jti?.let(jwtRevocationService::revoke)
        }
        // Drop the server-side session from Valkey so the SESSION cookie can't
        // outlive the logout (the JWT cookie alone expiring is not enough).
        request.getSession(false)?.invalidate()
        authTokenCookieService.clearAuthCookie(response)
    }

    private fun resolveToken(request: HttpServletRequest): String? {
        val header = request.getHeader("Authorization")
        if (!header.isNullOrBlank() && header.startsWith("Bearer ")) {
            return header.substring(7).trim().takeIf { it.isNotBlank() }
        }
        return authTokenCookieService.resolveToken(request)
    }
}

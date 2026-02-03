package net.blueshell.api.controller

import io.swagger.v3.oas.annotations.tags.Tag
import jakarta.annotation.security.PermitAll
import net.blueshell.api.auth.JWTAuthBase
import net.blueshell.api.auth.JwtTokenUtil
import net.blueshell.api.dto.request.JwtRequest
import net.blueshell.api.dto.response.AuthenticationDTO
import net.blueshell.api.service.UserService
import org.springframework.beans.factory.annotation.Value
import org.springframework.security.authentication.AuthenticationManager
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken
import org.springframework.validation.annotation.Validated
import org.springframework.web.bind.annotation.PostMapping
import org.springframework.web.bind.annotation.RequestBody
import org.springframework.web.bind.annotation.RestController

@RestController
@Tag(name = "Authentication")
class AuthenticationController(
    private val authenticationManager: AuthenticationManager,
    private val jwtTokenUtil: JwtTokenUtil,
    private val users: UserService
) : JWTAuthBase() {

    @Value("\${app.jwt.expiration}")
    private val expiration: Long? = null

    @PostMapping(("/auth"))
    @PermitAll
    fun authenticate(@Validated @RequestBody authenticationRequest: JwtRequest): AuthenticationDTO {
        authenticate(
            authenticationRequest.getUsername(),
            authenticationRequest.getPassword()
        )

        val user = users.findByUsername(authenticationRequest.getUsername())
        val token = jwtTokenUtil.generateToken(user)
        val expirationTime = System.currentTimeMillis() + expiration!!

        return AuthenticationDTO(
            token,
            user.getId(),
            user.getUsername(),
            expirationTime,
            user.getInheritedRoles(),
            user.getAddressId()
        )
    }

    private fun authenticate(username: String?, password: String?) {
        authenticationManager.authenticate(
            UsernamePasswordAuthenticationToken(username, password)
        )
    }
}

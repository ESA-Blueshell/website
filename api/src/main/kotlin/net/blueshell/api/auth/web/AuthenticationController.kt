package net.blueshell.api.auth.web

import io.swagger.v3.oas.annotations.tags.Tag
import jakarta.annotation.security.PermitAll
import net.blueshell.api.auth.security.JWTAuthBase
import net.blueshell.api.auth.security.JwtTokenUtil
import net.blueshell.api.auth.dto.request.JwtRequest
import net.blueshell.api.auth.dto.response.AuthenticationDTO
import net.blueshell.api.user.service.UserService
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

    @Value($$"${app.jwt.expiration}")
    private var expiration: Long = 0

    @PostMapping(("/auth"))
    @PermitAll
    fun authenticate(@Validated @RequestBody authenticationRequest: JwtRequest): AuthenticationDTO {
        val username = requireNotNull(authenticationRequest.username) { "Username is required" }
        val password = requireNotNull(authenticationRequest.password) { "Password is required" }
        authenticate(username, password)

        val user = users.findByUsername(username)
        val token = jwtTokenUtil.generateToken(user)
        val expirationTime = System.currentTimeMillis() + expiration

        return AuthenticationDTO(
            token,
            user.id!!,
            user.username,
            expirationTime,
            user.inheritedRoles as MutableSet,
            user.addressId
        )
    }

    private fun authenticate(username: String, password: String) {
        authenticationManager.authenticate(
            UsernamePasswordAuthenticationToken(username, password)
        )
    }
}

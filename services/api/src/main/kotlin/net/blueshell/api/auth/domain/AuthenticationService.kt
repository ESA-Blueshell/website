package net.blueshell.api.auth.domain

import net.blueshell.api.user.api.UserService
import org.springframework.security.authentication.AuthenticationManager
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken
import org.springframework.stereotype.Service
import net.blueshell.api.auth.api.TokenGenerator

@Service
class AuthenticationService(
    private val authenticationManager: AuthenticationManager,
    private val tokenGenerator: TokenGenerator,
    private val users: UserService
) {

    fun authenticate(username: String, password: String): AuthenticationSession {
        authenticationManager.authenticate(UsernamePasswordAuthenticationToken(username, password))

        val user = users.findByUsername(username)
        val token = tokenGenerator.generateToken(user.username)
        val expirationTime = System.currentTimeMillis() + tokenGenerator.expirationMs

        return AuthenticationSession(
            token = token,
            userId = user.id!!,
            username = user.username,
            expiresAtEpochMs = expirationTime,
            roles = user.inheritedRoles,
            addressId = user.addressId
        )
    }
}

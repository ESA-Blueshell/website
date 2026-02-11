package net.blueshell.api.domain.auth.security

import net.blueshell.api.domain.user.application.UserService
import org.springframework.security.authentication.BadCredentialsException
import org.springframework.security.authentication.DisabledException
import org.springframework.security.authentication.AuthenticationProvider
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken
import org.springframework.security.core.Authentication
import org.springframework.security.core.AuthenticationException
import org.springframework.security.crypto.password.PasswordEncoder
import org.springframework.stereotype.Component
import org.springframework.web.server.ResponseStatusException

@Component
class UserAuthenticationProvider(
    private val users: UserService,
    private val passwordEncoder: PasswordEncoder
) : AuthenticationProvider {
    @Throws(AuthenticationException::class)
    override fun authenticate(authentication: Authentication): Authentication {
        val username = authentication.name ?: ""
        val rawPassword = authentication.credentials?.toString() ?: ""

        val user = try {
            users.findByUsername(username)
        } catch (ex: ResponseStatusException) {
            throw BadCredentialsException("Invalid credentials", ex)
        }

        if (!user.enabled) {
            throw DisabledException("User is disabled")
        }
        if (!passwordEncoder.matches(rawPassword, user.password)) {
            throw BadCredentialsException("Invalid credentials")
        }

        return UsernamePasswordAuthenticationToken(user, null, user.authorities)
    }

    override fun supports(authentication: Class<*>): Boolean {
        return UsernamePasswordAuthenticationToken::class.java.isAssignableFrom(authentication)
    }
}

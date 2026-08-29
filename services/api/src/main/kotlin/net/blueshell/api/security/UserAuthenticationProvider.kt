package net.blueshell.api.security

import net.blueshell.api.user.api.UserService
import net.blueshell.api.user.api.UserNotFoundException
import org.springframework.security.authentication.AuthenticationProvider
import org.springframework.security.authentication.BadCredentialsException
import org.springframework.security.authentication.DisabledException
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken
import org.springframework.security.core.Authentication
import org.springframework.security.core.AuthenticationException
import org.springframework.security.crypto.password.PasswordEncoder
import org.springframework.stereotype.Component

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
            users.loadUserPrincipalByUsername(username)
        } catch (ex: UserNotFoundException) {
            throw BadCredentialsException("Invalid credentials", ex)
        }

        // Before anything else about the account is looked at. The service account is the site
        // itself, it holds a role that inherits administrator, and nobody signs in as it — so
        // enabling it or resetting its password must not be enough to get in. Refused as bad
        // credentials rather than as disabled, because which accounts are not people is not
        // something a sign-in page has any business telling whoever is trying.
        if (user.isServiceAccount) {
            throw BadCredentialsException("Invalid credentials")
        }
        if (!user.isEnabled) {
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

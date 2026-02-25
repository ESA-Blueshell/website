package net.blueshell.api.domain.auth.application

import net.blueshell.api.domain.auth.domain.service.TokenGenerator
import net.blueshell.api.domain.user.application.UserService
import net.blueshell.api.domain.user.persistence.User
import net.blueshell.api.shared.enums.Role
import org.assertj.core.api.Assertions.assertThat
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.assertThrows
import org.mockito.kotlin.any
import org.mockito.kotlin.mock
import org.mockito.kotlin.whenever
import org.springframework.security.authentication.AuthenticationManager
import org.springframework.security.authentication.BadCredentialsException

class AuthenticationServiceTest {

    private val authenticationManager = mock<AuthenticationManager>()
    private val tokenGenerator = mock<TokenGenerator>()
    private val users = mock<UserService>()
    private val service = AuthenticationService(authenticationManager, tokenGenerator, users)

    private fun user(
        id: Long = 1L,
        username: String = "john",
        roles: Set<Role> = setOf(Role.MEMBER),
        addressId: Long? = 10L,
    ): User {
        val user = mock<User>()
        whenever(user.id).thenReturn(id)
        whenever(user.username).thenReturn(username)
        whenever(user.inheritedRoles).thenReturn(roles)
        whenever(user.addressId).thenReturn(addressId)
        return user
    }

    @Test
    fun `authenticate returns session with correct userId, roles, and token`() {
        val user = user(id = 5L, username = "john", roles = setOf(Role.MEMBER), addressId = 10L)
        whenever(authenticationManager.authenticate(any())).thenReturn(mock())
        whenever(users.findByUsername("john")).thenReturn(user)
        whenever(tokenGenerator.generateToken("john")).thenReturn("jwt-token")
        whenever(tokenGenerator.expirationMs).thenReturn(60_000L)

        val session = service.authenticate("john", "Passw0rd!")

        assertThat(session.token).isEqualTo("jwt-token")
        assertThat(session.userId).isEqualTo(5L)
        assertThat(session.username).isEqualTo("john")
        assertThat(session.roles).containsExactly(Role.MEMBER)
        assertThat(session.addressId).isEqualTo(10L)
    }

    @Test
    fun `authenticate propagates BadCredentialsException for wrong password`() {
        whenever(authenticationManager.authenticate(any()))
            .thenThrow(BadCredentialsException("Bad credentials"))

        assertThrows<BadCredentialsException> {
            service.authenticate("john", "wrong")
        }
    }
}

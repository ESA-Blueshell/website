package net.blueshell.api.security

import net.blueshell.api.shared.enums.Role
import net.blueshell.api.shared.security.UserPrincipal
import net.blueshell.api.user.api.UserService
import org.assertj.core.api.Assertions.assertThat
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.assertThrows
import org.mockito.kotlin.mock
import org.mockito.kotlin.whenever
import org.springframework.security.authentication.BadCredentialsException
import org.springframework.security.authentication.LockedException
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken
import org.springframework.security.crypto.password.PasswordEncoder

class UserAuthenticationProviderTest {
    private val users = mock<UserService>()
    private val passwords = mock<PasswordEncoder>()
    private val provider = UserAuthenticationProvider(users, passwords)

    private fun holding(locked: Boolean) =
        whenever(users.loadUserPrincipalByUsername("alice"))
            .thenReturn(UserPrincipal(7, "alice", "hash", true, setOf(Role.MEMBER), null, null, locked = locked))

    @Test
    fun `a locked account is told so only to somebody holding its password`() {
        holding(locked = true)
        whenever(passwords.matches("right", "hash")).thenReturn(true)

        assertThrows<BadCredentialsException> { provider.authenticate(UsernamePasswordAuthenticationToken("alice", "wrong")) }
        assertThrows<LockedException> { provider.authenticate(UsernamePasswordAuthenticationToken("alice", "right")) }
    }

    @Test
    fun `an account not locked signs in with its password`() {
        holding(locked = false)
        whenever(passwords.matches("right", "hash")).thenReturn(true)

        assertThat(provider.authenticate(UsernamePasswordAuthenticationToken("alice", "right")).isAuthenticated).isTrue()
    }
}

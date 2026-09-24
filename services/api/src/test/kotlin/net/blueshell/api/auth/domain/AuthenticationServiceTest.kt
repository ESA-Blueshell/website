package net.blueshell.api.auth.domain

import net.blueshell.api.security.Browser
import net.blueshell.api.security.JwtTokenUtil
import net.blueshell.api.security.SignIns
import net.blueshell.api.shared.enums.Role
import net.blueshell.api.shared.time.SettableClock
import net.blueshell.api.testsupport.InMemorySignInStore
import net.blueshell.api.user.api.UserService
import net.blueshell.api.user.persistence.User
import org.assertj.core.api.Assertions.assertThat
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.assertThrows
import org.mockito.kotlin.any
import org.mockito.kotlin.mock
import org.mockito.kotlin.whenever
import org.springframework.context.ApplicationEventPublisher
import org.springframework.security.authentication.AuthenticationManager
import org.springframework.security.authentication.BadCredentialsException
import java.time.Duration

class AuthenticationServiceTest {
    private val clock = SettableClock()
    private val signIns =
        SignIns(
            InMemorySignInStore(),
            JwtTokenUtil("2goYh5PqH6dPkWWXLUJQ4QY6nD2YgR5qk9+6Yu8aITR7cfwxkuNolL9zkgf2qHFxifWdbxG+E+XqMIKkt3ibDw==", "api", "web", clock),
            clock,
            ApplicationEventPublisher {},
            Duration.ofDays(30),
            Duration.ofDays(14),
            Duration.ofMinutes(5),
            Duration.ofSeconds(60),
        )
    private val authenticationManager = mock<AuthenticationManager>()
    private val users = mock<UserService>()
    private val service = AuthenticationService(authenticationManager, users, signIns)

    private fun user(): User {
        val user = mock<User>()
        whenever(user.id).thenReturn(5L)
        whenever(user.username).thenReturn("john")
        whenever(user.inheritedRoles).thenReturn(setOf(Role.MEMBER, Role.GUEST))
        whenever(user.addressId).thenReturn(10L)
        return user
    }

    @Test
    fun `a right password opens a sign-in for the person, in the browser it came from`() {
        whenever(authenticationManager.authenticate(any())).thenReturn(mock())
        val john = user()
        whenever(users.findByUsername("john")).thenReturn(john)

        val signedIn = service.signIn("john", "Passw0rd!", Browser("Firefox", "Linux"))

        assertThat(signedIn.signer).isEqualTo(Signer(5L, "john", listOf(Role.GUEST, Role.MEMBER), 10L))
        assertThat(signedIn.issued.signIn.userId).isEqualTo(5L)
        assertThat(signedIn.issued.signIn.browser).isEqualTo(Browser("Firefox", "Linux"))
        assertThat(signIns.isLive(signedIn.issued.signIn.id)).isTrue()
    }

    @Test
    fun `a wrong password opens nothing`() {
        whenever(authenticationManager.authenticate(any())).thenThrow(BadCredentialsException("Bad credentials"))

        assertThrows<BadCredentialsException> { service.signIn("john", "wrong", Browser.UNKNOWN) }
    }
}

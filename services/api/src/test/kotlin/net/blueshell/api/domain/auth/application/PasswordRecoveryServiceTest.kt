package net.blueshell.api.domain.auth.application

import net.blueshell.api.domain.auth.application.factory.RecoveryTokenFactory
import net.blueshell.api.domain.auth.domain.service.RecoveryTokenValidator
import net.blueshell.api.domain.auth.persistence.RecoveryToken
import net.blueshell.api.domain.user.application.UserService
import net.blueshell.api.domain.user.application.exception.UserNotFoundException
import net.blueshell.api.domain.user.persistence.User
import net.blueshell.api.shared.enums.TokenPurpose
import org.assertj.core.api.Assertions.assertThat
import org.junit.jupiter.api.Test
import org.mockito.kotlin.any
import org.mockito.kotlin.eq
import org.mockito.kotlin.mock
import org.mockito.kotlin.verify
import org.mockito.kotlin.whenever
import java.time.Duration

class PasswordRecoveryServiceTest {

    private val users = mock<UserService>()
    private val tokenFactory = mock<RecoveryTokenFactory>()
    private val tokenValidator = mock<RecoveryTokenValidator>()
    private val service = PasswordRecoveryService(users, tokenFactory, tokenValidator)

    private fun user(id: Long = 1L, username: String = "john"): User {
        val user = mock<User>()
        whenever(user.id).thenReturn(id)
        whenever(user.username).thenReturn(username)
        return user
    }

    @Test
    fun `requestPasswordReset returns dispatch when user exists`() {
        val user = user(id = 5L, username = "john")
        whenever(users.findByUsername("john")).thenReturn(user)
        whenever(tokenFactory.issue(eq(user), eq(TokenPurpose.PASSWORD_RESET), any<Duration>()))
            .thenReturn("sel.ver")

        val result = service.requestPasswordReset("john")

        assertThat(result).isNotNull
        assertThat(result!!.userId).isEqualTo(5L)
        assertThat(result.rawToken).isEqualTo("sel.ver")
        assertThat(result.type).isEqualTo(TokenPurpose.PASSWORD_RESET)
    }

    @Test
    fun `requestPasswordReset returns null when user not found`() {
        whenever(users.findByUsername("unknown")).thenThrow(UserNotFoundException("unknown"))

        val result = service.requestPasswordReset("unknown")

        assertThat(result).isNull()
    }

    @Test
    fun `setPassword verifies token and updates password`() {
        val user = user(id = 10L)
        val token = mock<RecoveryToken>()
        whenever(token.user).thenReturn(user)
        whenever(tokenValidator.verify("sel.ver", TokenPurpose.PASSWORD_RESET)).thenReturn(token)

        service.setPassword("sel.ver", "NewPass123!")

        verify(users).updatePassword(10L, "NewPass123!")
    }

    @Test
    fun `setPassword consumes token after successful password update`() {
        val user = user(id = 10L)
        val token = mock<RecoveryToken>()
        whenever(token.user).thenReturn(user)
        whenever(tokenValidator.verify("sel.ver", TokenPurpose.PASSWORD_RESET)).thenReturn(token)

        service.setPassword("sel.ver", "NewPass123!")

        verify(tokenFactory).consume(token)
    }
}

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

class UserActivationServiceTest {

    private val users = mock<UserService>()
    private val tokenFactory = mock<RecoveryTokenFactory>()
    private val tokenValidator = mock<RecoveryTokenValidator>()
    private val service = UserActivationService(users, tokenFactory, tokenValidator)

    private fun user(id: Long = 1L, enabled: Boolean = false): User {
        val user = mock<User>()
        whenever(user.id).thenReturn(id)
        whenever(user.enabled).thenReturn(enabled)
        return user
    }

    private fun recoveryToken(
        user: User = user(),
        type: TokenPurpose = TokenPurpose.USER_ACTIVATION,
    ): RecoveryToken {
        val token = mock<RecoveryToken>()
        whenever(token.user).thenReturn(user)
        whenever(token.type).thenReturn(type)
        return token
    }

    @Test
    fun `activateUser enables user and consumes token`() {
        val user = user(id = 5L)
        val token = recoveryToken(user = user)
        whenever(tokenValidator.verify("sel.ver", TokenPurpose.USER_ACTIVATION)).thenReturn(token)

        val result = service.activateUser("sel.ver")

        verify(users).activateUser(5L)
        verify(tokenFactory).consume(token)
        assertThat(result).isSameAs(user)
    }

    @Test
    fun `activateMember sets username, password, enables user, and consumes token`() {
        val user = user(id = 7L)
        val token = recoveryToken(user = user, type = TokenPurpose.MEMBER_ACTIVATION)
        whenever(tokenValidator.verify("sel.ver", TokenPurpose.MEMBER_ACTIVATION)).thenReturn(token)

        service.activateMember("sel.ver", "newuser", "Pass123!")

        verify(users).setUsernameAndPassword(7L, "newuser", "Pass123!")
        verify(users).activateUser(7L)
        verify(tokenFactory).consume(token)
    }

    @Test
    fun `requestUserActivation returns null when user is already enabled`() {
        val user = user(id = 3L, enabled = true)
        whenever(users.findByUsername("active")).thenReturn(user)

        val result = service.requestUserActivation("active")

        assertThat(result).isNull()
    }

    @Test
    fun `requestUserActivation issues token when user exists and is disabled`() {
        val user = user(id = 4L, enabled = false)
        whenever(users.findByUsername("disabled")).thenReturn(user)
        whenever(tokenFactory.issue(eq(user), eq(TokenPurpose.USER_ACTIVATION), any<Duration>()))
            .thenReturn("sel.ver")

        val result = service.requestUserActivation("disabled")

        assertThat(result).isNotNull
        assertThat(result!!.userId).isEqualTo(4L)
        assertThat(result.rawToken).isEqualTo("sel.ver")
        assertThat(result.type).isEqualTo(TokenPurpose.USER_ACTIVATION)
    }

    @Test
    fun `requestUserActivation returns null when user not found`() {
        whenever(users.findByUsername("ghost")).thenThrow(UserNotFoundException("ghost"))

        val result = service.requestUserActivation("ghost")

        assertThat(result).isNull()
    }

    @Test
    fun `requestActivationEmail returns null when user is already enabled`() {
        val user = user(id = 8L, enabled = true)
        whenever(users.findById(8L)).thenReturn(user)

        val result = service.requestActivationEmail(8L)

        assertThat(result).isNull()
    }

    @Test
    fun `requestActivationEmail uses MEMBER_ACTIVATION type when unconsumed member token exists`() {
        val user = user(id = 9L, enabled = false)
        whenever(users.findById(9L)).thenReturn(user)
        val memberToken = recoveryToken(user = user, type = TokenPurpose.MEMBER_ACTIVATION)
        whenever(tokenValidator.findUnconsumedByUserId(9L)).thenReturn(listOf(memberToken))
        whenever(tokenFactory.issue(eq(user), eq(TokenPurpose.MEMBER_ACTIVATION), any<Duration>()))
            .thenReturn("member.token")

        val result = service.requestActivationEmail(9L)

        assertThat(result).isNotNull
        assertThat(result!!.type).isEqualTo(TokenPurpose.MEMBER_ACTIVATION)
    }

    @Test
    fun `requestActivationEmail uses USER_ACTIVATION type when only user activation token exists`() {
        val user = user(id = 11L, enabled = false)
        whenever(users.findById(11L)).thenReturn(user)
        val userToken = recoveryToken(user = user, type = TokenPurpose.USER_ACTIVATION)
        whenever(tokenValidator.findUnconsumedByUserId(11L)).thenReturn(listOf(userToken))
        whenever(tokenFactory.issue(eq(user), eq(TokenPurpose.USER_ACTIVATION), any<Duration>()))
            .thenReturn("user.token")

        val result = service.requestActivationEmail(11L)

        assertThat(result).isNotNull
        assertThat(result!!.type).isEqualTo(TokenPurpose.USER_ACTIVATION)
    }

    @Test
    fun `requestActivationEmail returns null when no unconsumed tokens exist`() {
        val user = user(id = 12L, enabled = false)
        whenever(users.findById(12L)).thenReturn(user)
        whenever(tokenValidator.findUnconsumedByUserId(12L)).thenReturn(emptyList())

        val result = service.requestActivationEmail(12L)

        assertThat(result).isNull()
    }

    @Test
    fun `issueActivationForNewUser uses 7-day TTL and MEMBER_ACTIVATION for board-created users`() {
        val user = user(id = 20L)
        whenever(users.findById(20L)).thenReturn(user)
        whenever(tokenFactory.issue(eq(user), eq(TokenPurpose.MEMBER_ACTIVATION), eq(Duration.ofDays(7))))
            .thenReturn("board.token")

        val result = service.issueActivationForNewUser(20L, createdByBoard = true)

        assertThat(result.type).isEqualTo(TokenPurpose.MEMBER_ACTIVATION)
        assertThat(result.rawToken).isEqualTo("board.token")
        verify(tokenFactory).issue(user, TokenPurpose.MEMBER_ACTIVATION, Duration.ofDays(7))
    }

    @Test
    fun `issueActivationForNewUser uses 1-hour TTL and USER_ACTIVATION for self-registered users`() {
        val user = user(id = 21L)
        whenever(users.findById(21L)).thenReturn(user)
        whenever(tokenFactory.issue(eq(user), eq(TokenPurpose.USER_ACTIVATION), eq(Duration.ofHours(1))))
            .thenReturn("self.token")

        val result = service.issueActivationForNewUser(21L, createdByBoard = false)

        assertThat(result.type).isEqualTo(TokenPurpose.USER_ACTIVATION)
        assertThat(result.rawToken).isEqualTo("self.token")
        verify(tokenFactory).issue(user, TokenPurpose.USER_ACTIVATION, Duration.ofHours(1))
    }
}

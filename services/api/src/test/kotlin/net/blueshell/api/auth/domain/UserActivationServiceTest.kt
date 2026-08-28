package net.blueshell.api.auth.domain

import net.blueshell.api.auth.persistence.RecoveryToken
import net.blueshell.api.domain.user.application.UserService
import net.blueshell.api.domain.user.application.exception.UserNotFoundException
import net.blueshell.api.domain.user.persistence.User
import net.blueshell.api.shared.enums.TokenPurpose
import org.assertj.core.api.Assertions.assertThat
import org.assertj.core.api.Assertions.assertThatThrownBy
import org.junit.jupiter.api.Test
import org.mockito.kotlin.any
import org.mockito.kotlin.eq
import org.mockito.kotlin.mock
import org.mockito.kotlin.never
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

    @Test
    fun `revokeOutstandingActivations consumes only confirmation links`() {
        val activation = recoveryToken(type = TokenPurpose.USER_ACTIVATION)
        val signupSession = recoveryToken(type = TokenPurpose.SIGNUP_CONTINUATION)
        val reset = recoveryToken(type = TokenPurpose.PASSWORD_RESET)
        whenever(tokenValidator.findUnconsumedByUserId(9L))
            .thenReturn(listOf(activation, signupSession, reset))

        service.revokeOutstandingActivations(9L)

        // Correcting the address must not end the signup session the applicant is
        // still using, nor a password reset they may have asked for.
        verify(tokenFactory).consume(activation)
        verify(tokenFactory, never()).consume(signupSession)
        verify(tokenFactory, never()).consume(reset)
    }

    @Test
    fun `revokeOutstandingActivations is a no-op when nothing is outstanding`() {
        whenever(tokenValidator.findUnconsumedByUserId(9L)).thenReturn(emptyList())

        service.revokeOutstandingActivations(9L)

        verify(tokenFactory, never()).consume(any())
    }

    @Test
    fun `requestActivation issues the kind it was asked for, not the one outstanding`() {
        val user = user(id = 20L, enabled = false)
        whenever(users.findById(20L)).thenReturn(user)
        // Only a user-activation link is outstanding; a member activation is still sendable.
        val outstanding = recoveryToken(user = user, type = TokenPurpose.USER_ACTIVATION)
        whenever(tokenValidator.findUnconsumedByUserId(20L)).thenReturn(listOf(outstanding))
        whenever(tokenFactory.issue(eq(user), eq(TokenPurpose.MEMBER_ACTIVATION), any<Duration>()))
            .thenReturn("member.token")

        val result = service.requestActivation(20L, TokenPurpose.MEMBER_ACTIVATION)

        assertThat(result).isNotNull
        assertThat(result!!.type).isEqualTo(TokenPurpose.MEMBER_ACTIVATION)
        assertThat(result.rawToken).isEqualTo("member.token")
    }

    @Test
    fun `requestActivation sends with nothing outstanding, where the automatic choice cannot`() {
        val user = user(id = 21L, enabled = false)
        whenever(users.findById(21L)).thenReturn(user)
        whenever(tokenValidator.findUnconsumedByUserId(21L)).thenReturn(emptyList())
        whenever(tokenFactory.issue(eq(user), eq(TokenPurpose.MEMBER_ACTIVATION), any<Duration>()))
            .thenReturn("member.token")

        assertThat(service.requestActivation(21L, TokenPurpose.MEMBER_ACTIVATION)).isNotNull
        // The path that guesses gives up here, which is the gap this one closes.
        assertThat(service.requestActivationEmail(21L)).isNull()
    }

    @Test
    fun `requestActivation retires the outstanding link of the same kind first`() {
        val user = user(id = 22L, enabled = false)
        whenever(users.findById(22L)).thenReturn(user)
        val stale = recoveryToken(user = user, type = TokenPurpose.MEMBER_ACTIVATION)
        val otherKind = recoveryToken(user = user, type = TokenPurpose.USER_ACTIVATION)
        whenever(tokenValidator.findUnconsumedByUserId(22L)).thenReturn(listOf(stale, otherKind))
        whenever(tokenFactory.issue(eq(user), eq(TokenPurpose.MEMBER_ACTIVATION), any<Duration>()))
            .thenReturn("member.token")

        service.requestActivation(22L, TokenPurpose.MEMBER_ACTIVATION)

        // One live link per kind; a link of another kind is somebody else's business.
        verify(tokenFactory).consume(stale)
        verify(tokenFactory, never()).consume(otherKind)
    }

    @Test
    fun `requestActivation gives the member link seven days and the user link an hour`() {
        val user = user(id = 23L, enabled = false)
        whenever(users.findById(23L)).thenReturn(user)
        whenever(tokenValidator.findUnconsumedByUserId(23L)).thenReturn(emptyList())
        whenever(tokenFactory.issue(eq(user), any(), any<Duration>())).thenReturn("token")

        service.requestActivation(23L, TokenPurpose.MEMBER_ACTIVATION)
        verify(tokenFactory).issue(user, TokenPurpose.MEMBER_ACTIVATION, Duration.ofDays(7))

        service.requestActivation(23L, TokenPurpose.USER_ACTIVATION)
        verify(tokenFactory).issue(user, TokenPurpose.USER_ACTIVATION, Duration.ofHours(1))
    }

    @Test
    fun `requestActivation returns null for an account that is already active`() {
        val active = user(id = 24L, enabled = true)
        whenever(users.findById(24L)).thenReturn(active)

        assertThat(service.requestActivation(24L, TokenPurpose.MEMBER_ACTIVATION)).isNull()
        verify(tokenFactory, never()).issue(any(), any(), any())
    }

    @Test
    fun `requestActivation refuses a purpose that is not an activation`() {
        listOf(TokenPurpose.PASSWORD_RESET, TokenPurpose.SIGNUP_CONTINUATION).forEach { purpose ->
            assertThatThrownBy { service.requestActivation(25L, purpose) }
                .isInstanceOf(IllegalArgumentException::class.java)
                .hasMessageContaining("not an activation")
        }
    }
}

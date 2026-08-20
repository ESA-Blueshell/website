package net.blueshell.api.domain.auth.application

import net.blueshell.api.domain.auth.application.exception.ConsumedRecoveryTokenException
import net.blueshell.api.domain.auth.application.exception.ExpiredRecoveryTokenException
import net.blueshell.api.domain.auth.application.exception.InvalidRecoveryTokenException
import net.blueshell.api.domain.auth.application.exception.InvalidTokenTypeException
import net.blueshell.api.domain.auth.application.exception.MalformedRecoveryTokenException
import net.blueshell.api.domain.auth.application.exception.TokenVerificationFailedException
import net.blueshell.api.domain.auth.application.factory.RecoveryTokenFactory
import net.blueshell.api.domain.auth.domain.service.RecoveryTokenValidator
import net.blueshell.api.domain.auth.persistence.RecoveryToken
import net.blueshell.api.domain.user.persistence.User
import net.blueshell.api.shared.enums.TokenPurpose
import org.assertj.core.api.Assertions.assertThat
import org.assertj.core.api.Assertions.assertThatThrownBy
import org.junit.jupiter.api.Test
import org.mockito.kotlin.mock
import org.mockito.kotlin.never
import org.mockito.kotlin.verify
import org.mockito.kotlin.whenever
import java.time.Duration
import java.time.Instant

// One test per rejection path: a single "rejects a bad token" case would keep
// passing while five of the six rejections quietly stopped working.
class SignupTokenServiceTest {

    private val tokenFactory = mock<RecoveryTokenFactory>()
    private val tokenValidator = mock<RecoveryTokenValidator>()
    private val users = mock<net.blueshell.api.domain.user.application.UserService>()
    private val service = SignupTokenService(tokenFactory, tokenValidator, users)

    private fun user(id: Long? = 7L, email: String = "lena@example.com"): User {
        val user = mock<User>()
        whenever(user.id).thenReturn(id)
        whenever(user.email).thenReturn(email)
        return user
    }

    private fun token(
        purpose: TokenPurpose = TokenPurpose.SIGNUP_CONTINUATION,
        owner: User = user(),
    ): RecoveryToken {
        val token = mock<RecoveryToken>()
        whenever(token.type).thenReturn(purpose)
        whenever(token.user).thenReturn(owner)
        return token
    }

    // ── issue ────────────────────────────────────────────────────────────────

    @Test
    fun `issue returns the raw token for the signup purpose`() {
        val applicant = user(id = 7L)
        whenever(tokenFactory.issue(applicant, TokenPurpose.SIGNUP_CONTINUATION, SignupTokenService.TTL))
            .thenReturn("selector.verifier")

        val session = service.issue(applicant)

        assertThat(session.token).isEqualTo("selector.verifier")
        assertThat(session.userId).isEqualTo(7L)
        assertThat(session.email).isEqualTo("lena@example.com")
    }

    @Test
    fun `issue uses a two hour lifetime`() {
        assertThat(SignupTokenService.TTL).isEqualTo(Duration.ofHours(2))
    }

    @Test
    fun `issue reports an expiry inside the lifetime window`() {
        val applicant = user()
        whenever(tokenFactory.issue(applicant, TokenPurpose.SIGNUP_CONTINUATION, SignupTokenService.TTL))
            .thenReturn("selector.verifier")
        val before = Instant.now()

        val session = service.issue(applicant)

        assertThat(session.expiresAt)
            .isAfterOrEqualTo(before.plus(SignupTokenService.TTL).minusSeconds(5))
            .isBeforeOrEqualTo(Instant.now().plus(SignupTokenService.TTL))
    }

    @Test
    fun `issue refuses an unsaved user`() {
        val unsaved = user(id = null)

        assertThatThrownBy { service.issue(unsaved) }
            .isInstanceOf(IllegalArgumentException::class.java)
            .hasMessageContaining("unsaved user")
    }

    // ── resolveUser: the account comes from the token, never the request ─────

    @Test
    fun `resolveAccount returns the account the token belongs to`() {
        val owner = user(id = 42L)
        // Built before the outer stub — Mockito rejects nested stubbing.
        val ownedToken = token(owner = owner)
        whenever(tokenValidator.verify("sel.ver", TokenPurpose.SIGNUP_CONTINUATION)).thenReturn(ownedToken)
        whenever(users.findById(42L)).thenReturn(owner)

        assertThat(service.resolveAccount("sel.ver").user).isSameAs(owner)
    }

    @Test
    fun `resolveAccount only ever asks for the signup purpose`() {
        val owner = user()
        val signupToken = token(owner = owner)
        whenever(tokenValidator.verify("sel.ver", TokenPurpose.SIGNUP_CONTINUATION)).thenReturn(signupToken)
        whenever(users.findById(7L)).thenReturn(owner)

        service.resolveAccount("sel.ver")

        verify(tokenValidator).verify("sel.ver", TokenPurpose.SIGNUP_CONTINUATION)
        verify(tokenValidator, never()).verify("sel.ver", TokenPurpose.USER_ACTIVATION)
        verify(tokenValidator, never()).verify("sel.ver", TokenPurpose.PASSWORD_RESET)
        verify(tokenValidator, never()).verify("sel.ver", TokenPurpose.MEMBER_ACTIVATION)
    }

    @Test
    fun `resolveAccount refuses a token whose owner has no id`() {
        val orphan = user(id = null)
        val orphanToken = token(owner = orphan)
        whenever(tokenValidator.verify("sel.ver", TokenPurpose.SIGNUP_CONTINUATION)).thenReturn(orphanToken)

        assertThatThrownBy { service.resolveAccount("sel.ver") }
            .isInstanceOf(IllegalArgumentException::class.java)
            .hasMessageContaining("no owner")
    }

    @Test
    fun `resolveAccount propagates a malformed token`() =
        assertResolveRejects(MalformedRecoveryTokenException("no separator"))

    @Test
    fun `resolveAccount propagates an unknown selector`() =
        assertResolveRejects(InvalidRecoveryTokenException("not found"))

    @Test
    fun `resolveAccount propagates a token minted for another purpose`() =
        assertResolveRejects(InvalidTokenTypeException("wrong purpose"))

    @Test
    fun `resolveAccount propagates an expired token`() =
        assertResolveRejects(ExpiredRecoveryTokenException("expired"))

    @Test
    fun `resolveAccount propagates a retired token`() =
        assertResolveRejects(ConsumedRecoveryTokenException("already used"))

    @Test
    fun `resolveAccount propagates a token whose verifier does not match`() =
        assertResolveRejects(TokenVerificationFailedException("bad verifier"))

    private fun assertResolveRejects(failure: RuntimeException) {
        whenever(tokenValidator.verify("sel.ver", TokenPurpose.SIGNUP_CONTINUATION)).thenThrow(failure)

        assertThatThrownBy { service.resolveAccount("sel.ver") }.isSameAs(failure)
    }

    // ── retire ───────────────────────────────────────────────────────────────

    @Test
    fun `retire consumes the live signup token`() {
        val signupToken = token()
        whenever(tokenValidator.findUnconsumedByUserId(7L)).thenReturn(listOf(signupToken))

        service.retire(7L)

        verify(tokenFactory).consume(signupToken)
    }

    @Test
    fun `retire leaves tokens of other purposes alone`() {
        val activation = token(purpose = TokenPurpose.USER_ACTIVATION)
        val reset = token(purpose = TokenPurpose.PASSWORD_RESET)
        val signupToken = token()
        whenever(tokenValidator.findUnconsumedByUserId(7L))
            .thenReturn(listOf(activation, reset, signupToken))

        service.retire(7L)

        // Must not invalidate an activation link the applicant is about to click.
        verify(tokenFactory).consume(signupToken)
        verify(tokenFactory, never()).consume(activation)
        verify(tokenFactory, never()).consume(reset)
    }

    @Test
    fun `retire is a no-op when there is nothing live`() {
        whenever(tokenValidator.findUnconsumedByUserId(7L)).thenReturn(emptyList())

        service.retire(7L)

        verify(tokenFactory, never()).consume(org.mockito.kotlin.any())
    }
}

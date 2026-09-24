package net.blueshell.api.auth.domain

import net.blueshell.api.auth.persistence.RecoveryToken
import net.blueshell.api.auth.persistence.SecurityEventKind
import net.blueshell.api.shared.enums.TokenPurpose
import net.blueshell.api.auth.domain.twofactor.Challenge
import net.blueshell.api.auth.domain.twofactor.Challenges
import net.blueshell.api.auth.domain.twofactor.Proof
import net.blueshell.api.auth.domain.twofactor.ThrottledCodes
import net.blueshell.api.auth.domain.twofactor.TrustedBrowsers
import net.blueshell.api.auth.domain.twofactor.TwoFactor
import net.blueshell.api.auth.domain.twofactor.TwoFactorStanding
import net.blueshell.api.security.Browser
import net.blueshell.api.security.JwtTokenUtil
import net.blueshell.api.security.SignIns
import net.blueshell.api.shared.enums.Role
import net.blueshell.api.platform.config.SettableClock
import net.blueshell.api.testsupport.InMemorySignInStore
import net.blueshell.api.user.api.UserService
import net.blueshell.api.user.persistence.User
import org.assertj.core.api.Assertions.assertThat
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.assertThrows
import org.mockito.kotlin.any
import org.mockito.kotlin.anyOrNull
import org.mockito.kotlin.eq
import org.mockito.kotlin.mock
import org.mockito.kotlin.never
import org.mockito.kotlin.spy
import org.mockito.kotlin.verify
import org.mockito.kotlin.whenever
import org.springframework.context.ApplicationEventPublisher
import org.springframework.security.authentication.AuthenticationManager
import org.springframework.security.authentication.BadCredentialsException
import java.time.Duration
import java.time.Instant

class AuthenticationServiceTest {
    private val clock = SettableClock().apply { set(Instant.parse("2026-09-24T12:00:00Z")) }
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
    private val twoFactor = mock<TwoFactor>()
    private val challenges = mock<Challenges>()
    private val trustedBrowsers = mock<TrustedBrowsers>()
    private val events = mock<SecurityEvents>()
    private val tokens = mock<RecoveryTokenValidator>()
    private val tokenFactory = mock<RecoveryTokenFactory>()
    private val service =
        AuthenticationService(
            authenticationManager,
            users,
            signIns,
            twoFactor,
            challenges,
            ThrottledCodes(twoFactor, challenges, events),
            trustedBrowsers,
            events,
            tokens,
            tokenFactory,
            clock,
        )
    private val firefox = Browser("Firefox", "Linux")
    private val standing = TwoFactorStanding(on = false, backupCodesLeft = 0, required = false, offered = true)

    private fun user(twoFactor: Boolean = false): User {
        val user =
            User(
                username = "john",
                email = "john@example.com",
                password = "hash",
                initials = "J",
                firstName = "John",
                lastName = "Doe",
                roles = mutableSetOf(Role.MEMBER),
            )
        user.twoFactorSince = if (twoFactor) Instant.EPOCH else null
        val spied = spy(user)
        whenever(spied.id).thenReturn(5L)
        return spied
    }

    @BeforeEach
    fun setUp() {
        whenever(authenticationManager.authenticate(any())).thenReturn(mock())
        whenever(twoFactor.standing(5L)).thenReturn(standing)
    }

    @Test
    fun `a right password without two-factor opens a sign-in in the browser it came from`() {
        val john = user()
        whenever(users.findByUsername("john")).thenReturn(john)

        val outcome = service.signIn("john", "Passw0rd!", firefox) as SignInOutcome.SignedIn

        assertThat(outcome.signer.userId).isEqualTo(5L)
        assertThat(outcome.signer.roles).containsExactly(Role.ANONYMOUS, Role.GUEST, Role.MEMBER)
        assertThat(outcome.signer.twoFactor).isEqualTo(standing)
        assertThat(outcome.issued.signIn.browser).isEqualTo(firefox)
        assertThat(signIns.isLive(outcome.issued.signIn.id)).isTrue()
        verify(events).record(eq(5L), eq(SecurityEventKind.SIGNED_IN), any(), anyOrNull(), eq(firefox), anyOrNull())
    }

    @Test
    fun `a browser never seen before is told about, but not on the very first sign-in`() {
        val john = user()
        whenever(users.findByUsername("john")).thenReturn(john)
        whenever(events.isNewBrowser(5L, firefox)).thenReturn(true)

        service.signIn("john", "Passw0rd!", firefox)

        verify(events).record(eq(5L), eq(SecurityEventKind.NEW_BROWSER), any(), anyOrNull(), eq(firefox), anyOrNull())
    }

    @Test
    fun `with two-factor on, a right password is only a challenge`() {
        val john = user(twoFactor = true)
        whenever(users.findByUsername("john")).thenReturn(john)
        whenever(challenges.open(5L, firefox)).thenReturn(Challenge("c-1", 5L, firefox, clock.instant(), 0))

        val outcome = service.signIn("john", "Passw0rd!", firefox)

        assertThat(outcome).isEqualTo(SignInOutcome.Challenged("c-1"))
    }

    @Test
    fun `a trusted browser of the account's own skips the challenge`() {
        val john = user(twoFactor = true)
        whenever(users.findByUsername("john")).thenReturn(john)
        val rotated = TrustedBrowsers.Issued("sel.new", Duration.ofDays(29))
        whenever(trustedBrowsers.redeem("sel.old", 5L, firefox)).thenReturn(rotated)

        val outcome = service.signIn("john", "Passw0rd!", firefox, "sel.old") as SignInOutcome.SignedIn

        assertThat(outcome.trustedBrowser).isEqualTo(rotated)
        verify(challenges, never()).open(any(), any())
    }

    @Test
    fun `an account awaiting re-enrolment is not signed in by its password`() {
        val john = user().also { it.awaitingReenrolment = true }
        whenever(users.findByUsername("john")).thenReturn(john)

        assertThrows<ReenrolmentRequired> { service.signIn("john", "Passw0rd!", firefox) }
    }

    @Test
    fun `the password and the emailed link together open a sign-in after a two-factor reset`() {
        val john = user().also { it.awaitingReenrolment = true }
        whenever(users.findByUsername("john")).thenReturn(john)
        val link = RecoveryToken(john, TokenPurpose.TWO_FACTOR_REENROLMENT, "sel", "hash", clock.instant().plusSeconds(60))
        whenever(tokens.verify("sel.ver", TokenPurpose.TWO_FACTOR_REENROLMENT)).thenReturn(link)

        val outcome = service.reenrol("john", "Passw0rd!", "sel.ver", firefox)

        assertThat(outcome.signer.username).isEqualTo("john")
        assertThat(john.awaitingReenrolment).isFalse()
        verify(tokenFactory).consume(link)
        verify(users).update(john)
        verify(events).record(eq(5L), eq(SecurityEventKind.REENROLLED), anyOrNull(), anyOrNull(), anyOrNull(), anyOrNull())
        whenever(users.findById(5L)).thenReturn(john)
        assertThat(service.signerOf(5L).userId).isEqualTo(5L)
    }

    @Test
    fun `a re-enrolment link of somebody else's opens nothing`() {
        val john = user()
        whenever(users.findByUsername("john")).thenReturn(john)
        val other = User(
            username = "eve",
            email = "e@example.com",
            password = "h",
            initials = "E",
            firstName = "E",
            lastName = "V",
        ).also { it.id = 6 }
        whenever(tokens.verify("sel.ver", TokenPurpose.TWO_FACTOR_REENROLMENT))
            .thenReturn(RecoveryToken(other, TokenPurpose.TWO_FACTOR_REENROLMENT, "sel", "hash", clock.instant().plusSeconds(60)))

        assertThrows<InvalidRecoveryTokenException> { service.reenrol("john", "Passw0rd!", "sel.ver", firefox) }
        verify(tokenFactory, never()).consume(any())
    }

    @Test
    fun `a wrong password opens nothing`() {
        whenever(authenticationManager.authenticate(any())).thenThrow(BadCredentialsException("Bad credentials"))

        assertThrows<BadCredentialsException> { service.signIn("john", "wrong", firefox) }
    }

    @Test
    fun `a right code turns the challenge into a proved sign-in, and may trust the browser`() {
        val challenge = Challenge("c-1", 5L, firefox, clock.instant(), 0)
        whenever(challenges.find("c-1")).thenReturn(challenge)
        whenever(twoFactor.prove(5L, "123456")).thenReturn(Proof.AUTHENTICATOR_CODE)
        val john = user(twoFactor = true)
        whenever(users.findById(5L)).thenReturn(john)
        whenever(trustedBrowsers.trust(5L, firefox)).thenReturn(TrustedBrowsers.Issued("sel.ver", Duration.ofDays(30)))

        val outcome = service.answerChallenge("c-1", "123456", firefox, trustThisBrowser = true)

        assertThat(outcome.issued.signIn.methods).containsExactlyInAnyOrder("pwd", "otp")
        assertThat(outcome.issued.signIn.steppedUpAt).isEqualTo(clock.instant())
        assertThat(outcome.trustedBrowser?.cookieValue).isEqualTo("sel.ver")
        verify(challenges).close("c-1")
        verify(events).record(eq(5L), eq(SecurityEventKind.TRUSTED_BROWSER_ADDED), any(), anyOrNull(), anyOrNull(), anyOrNull())
    }

    @Test
    fun `a right code without trusting the browser trusts nothing`() {
        whenever(challenges.find("c-1")).thenReturn(Challenge("c-1", 5L, firefox, clock.instant(), 0))
        whenever(twoFactor.prove(5L, "123456")).thenReturn(Proof.AUTHENTICATOR_CODE)
        val john = user(twoFactor = true)
        whenever(users.findById(5L)).thenReturn(john)

        val outcome = service.answerChallenge("c-1", "123456", firefox, trustThisBrowser = false)

        assertThat(outcome.trustedBrowser).isNull()
        verify(trustedBrowsers, never()).trust(any(), any())
    }

    @Test
    fun `a wrong code costs a try and counts against the account`() {
        val challenge = Challenge("c-1", 5L, firefox, clock.instant(), 0)
        whenever(challenges.find("c-1")).thenReturn(challenge)
        whenever(challenges.fail(challenge)).thenReturn(4)
        whenever(challenges.countFailure(5L)).thenReturn(true)

        val refusal = assertThrows<WrongCode> { service.answerChallenge("c-1", "000000", firefox, false) }

        assertThat(refusal.facts).isEqualTo(mapOf("triesLeft" to 4))
        verify(events).record(eq(5L), eq(SecurityEventKind.CODE_LIMIT_REACHED), any(), anyOrNull(), anyOrNull(), anyOrNull())
    }

    @Test
    fun `a challenge from another browser, an expired one and a throttled account are refused`() {
        whenever(challenges.find("c-1")).thenReturn(Challenge("c-1", 5L, firefox, clock.instant(), 0))
        assertThrows<ChallengeExpired> { service.answerChallenge("c-1", "1", Browser("Chrome", "Linux"), false) }
        assertThrows<ChallengeExpired> { service.answerChallenge("gone", "1", firefox, false) }

        whenever(challenges.isThrottled(5L)).thenReturn(true)
        assertThrows<CodeLimitReached> { service.answerChallenge("c-1", "1", firefox, false) }
    }
}

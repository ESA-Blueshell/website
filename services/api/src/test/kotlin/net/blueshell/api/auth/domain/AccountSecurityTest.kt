package net.blueshell.api.auth.domain

import net.blueshell.api.auth.domain.twofactor.Challenges
import net.blueshell.api.auth.domain.twofactor.PendingSecret
import net.blueshell.api.auth.domain.twofactor.Proof
import net.blueshell.api.auth.domain.twofactor.TrustedBrowsers
import net.blueshell.api.auth.domain.twofactor.TwoFactor
import net.blueshell.api.auth.persistence.RecoveryToken
import net.blueshell.api.auth.persistence.SecurityEventKind
import net.blueshell.api.platform.config.SettableClock
import net.blueshell.api.security.Browser
import net.blueshell.api.security.SignIn
import net.blueshell.api.security.SignIns
import net.blueshell.api.security.StepUp
import net.blueshell.api.security.StepUpRequiredException
import net.blueshell.api.shared.enums.Role
import net.blueshell.api.shared.enums.TokenPurpose
import net.blueshell.api.shared.job.EmailJobs
import net.blueshell.api.shared.job.JobQueue
import net.blueshell.api.user.api.UserService
import net.blueshell.api.user.persistence.User
import org.assertj.core.api.Assertions.assertThat
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.Nested
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.assertThrows
import org.mockito.kotlin.any
import org.mockito.kotlin.anyOrNull
import org.mockito.kotlin.doThrow
import org.mockito.kotlin.eq
import org.mockito.kotlin.mock
import org.mockito.kotlin.never
import org.mockito.kotlin.times
import org.mockito.kotlin.verify
import org.mockito.kotlin.whenever
import org.springframework.data.domain.PageImpl
import org.springframework.data.domain.Pageable
import org.springframework.security.crypto.password.PasswordEncoder
import java.time.Instant

class AccountSecurityTest {
    private val clock = SettableClock().apply { set(Instant.parse("2026-09-24T12:00:00Z")) }
    private val users = mock<UserService>()
    private val passwords = mock<PasswordEncoder>()
    private val twoFactor = mock<TwoFactor>()
    private val challenges = mock<Challenges>()
    private val trustedBrowsers = mock<TrustedBrowsers>()
    private val signIns = mock<SignIns>()
    private val stepUp = mock<StepUp>()
    private val events = mock<SecurityEvents>()
    private val tokenFactory = mock<RecoveryTokenFactory>()
    private val tokenValidator = mock<RecoveryTokenValidator>()
    private val recovery = mock<RecoveryUseCases>()
    private val jobs = mock<JobQueue>()
    private val security =
        AccountSecurity(
            users,
            passwords,
            twoFactor,
            challenges,
            trustedBrowsers,
            signIns,
            stepUp,
            events,
            tokenFactory,
            tokenValidator,
            recovery,
            jobs,
            clock,
        )

    private val user =
        User(
            username = "alice",
            email = "alice@example.com",
            password = "hash",
            initials = "A",
            firstName = "Alice",
            lastName = "Doe",
            roles = mutableSetOf(Role.MEMBER),
        ).also { it.id = 7 }

    private fun recorded(kind: SecurityEventKind) =
        verify(events).record(eq(7L), eq(kind), any(), anyOrNull(), anyOrNull(), anyOrNull())

    private fun token(type: TokenPurpose) = RecoveryToken(user, type, "sel", "hash", clock.instant().plusSeconds(60))

    @BeforeEach
    fun setUp() {
        whenever(users.findById(7)).thenReturn(user)
        whenever(passwords.matches("right", "hash")).thenReturn(true)
        whenever(tokenFactory.issue(any(), any(), any())).thenReturn("sel.ver")
    }

    @Nested
    inner class SteppingUp {
        @Test
        fun `with two-factor a code proves the sign-in, and a wrong one counts against the account`() {
            user.twoFactorSince = clock.instant()
            whenever(twoFactor.prove(7, "123456")).thenReturn(Proof.AUTHENTICATOR_CODE)
            whenever(challenges.countFailure(7)).thenReturn(true)

            security.stepUp(7, "here", "123456", null)
            verify(signIns).recordStepUp("here", SignIn.METHOD_OTP)

            assertThrows<WrongCode> { security.stepUp(7, "here", "000000", null) }
            assertThrows<WrongCode> { security.stepUp(7, "here", null, "right") }
            verify(events, times(2)).record(eq(7L), eq(SecurityEventKind.CODE_LIMIT_REACHED), any(), anyOrNull(), anyOrNull(), anyOrNull())

            whenever(challenges.isThrottled(7)).thenReturn(true)
            assertThrows<CodeLimitReached> { security.stepUp(7, "here", "123456", null) }
        }

        @Test
        fun `without two-factor the password proves it`() {
            security.stepUp(7, "here", null, "right")
            verify(signIns).recordStepUp("here", null)

            assertThrows<WrongPassword> { security.stepUp(7, "here", null, "wrong") }
            assertThrows<WrongPassword> { security.stepUp(7, "here", null, null) }
        }
    }

    @Test
    fun `setting up asks for a step-up only when there is an app to replace`() {
        whenever(twoFactor.setUp(7, "right")).thenReturn(PendingSecret("otpauth://x", "KEY"))

        assertThat(security.setUpTwoFactor(7, "right").key).isEqualTo("KEY")
        verify(stepUp, never()).require()

        user.twoFactorSince = clock.instant()
        security.setUpTwoFactor(7, "right")
        verify(stepUp).require()
    }

    @Test
    fun `turning off and new backup codes ask for a step-up first`() {
        whenever(stepUp.require()).doThrow(StepUpRequiredException())

        assertThrows<StepUpRequiredException> { security.turnOffTwoFactor(7) }
        assertThrows<StepUpRequiredException> { security.regenerateBackupCodes(7) }
        verify(twoFactor, never()).turnOff(any())
    }

    @Test
    fun `turning off and new backup codes go through once proved`() {
        whenever(twoFactor.regenerateBackupCodes(7)).thenReturn(listOf("a"))

        security.turnOffTwoFactor(7)
        assertThat(security.regenerateBackupCodes(7)).containsExactly("a")

        verify(twoFactor).turnOff(7)
    }

    @Test
    fun `changing the password checks the old one, ends the other sign-ins and tells the person`() {
        assertThrows<WrongPassword> { security.changePassword(7, "here", "wrong", "Another123!") }

        security.changePassword(7, "here", "right", "Another123!")

        verify(users).updatePassword(7, "Another123!")
        verify(trustedBrowsers).forgetAll(7)
        verify(signIns).endAll(7, "here")
        recorded(SecurityEventKind.PASSWORD_CHANGED)
        verify(stepUp, never()).require()

        user.twoFactorSince = clock.instant()
        security.changePassword(7, "here", "right", "Another123!")
        verify(stepUp).require()
    }

    @Nested
    inner class MovingTheAddress {
        @Test
        fun `asking holds the new address, mails it a link and tells the old one`() {
            security.requestEmailChange(7, " New@Example.com ")

            verify(stepUp).require()
            assertThat(user.pendingEmail).isEqualTo("new@example.com")
            verify(tokenFactory).issue(user, TokenPurpose.EMAIL_CHANGE, AccountSecurity.EMAIL_CHANGE_TTL)
            verify(jobs).runAsync(EmailJobs.Recovery, EmailJobs.RecoveryPayload(7, "sel.ver", TokenPurpose.EMAIL_CHANGE))
            recorded(SecurityEventKind.EMAIL_CHANGE_REQUESTED)
        }

        @Test
        fun `an address that is somebody else's is refused, when asked and when confirmed`() {
            whenever(users.existsByEmailAndIdNot("taken@example.com", 7)).thenReturn(true)
            assertThrows<EmailTaken> { security.requestEmailChange(7, "taken@example.com") }

            user.pendingEmail = "taken@example.com"
            whenever(tokenValidator.verify("sel.ver", TokenPurpose.EMAIL_CHANGE)).thenReturn(token(TokenPurpose.EMAIL_CHANGE))
            assertThrows<EmailTaken> { security.confirmEmailChange("sel.ver") }
        }

        @Test
        fun `confirming moves the account and spends the link`() {
            user.pendingEmail = "new@example.com"
            val link = token(TokenPurpose.EMAIL_CHANGE)
            whenever(tokenValidator.verify("sel.ver", TokenPurpose.EMAIL_CHANGE)).thenReturn(link)

            security.confirmEmailChange("sel.ver")

            assertThat(user.email).isEqualTo("new@example.com")
            assertThat(user.pendingEmail).isNull()
            verify(tokenFactory).consume(link)
            recorded(SecurityEventKind.EMAIL_CHANGED)
        }

        @Test
        fun `a link with no move waiting behind it is refused`() {
            whenever(tokenValidator.verify("sel.ver", TokenPurpose.EMAIL_CHANGE)).thenReturn(token(TokenPurpose.EMAIL_CHANGE))

            assertThrows<InvalidRecoveryTokenException> { security.confirmEmailChange("sel.ver") }
        }
    }

    @Nested
    inner class Locking {
        @Test
        fun `a good lock link locks, drops a pending move and ends every sign-in`() {
            val link = token(TokenPurpose.ACCOUNT_LOCK)
            val move = token(TokenPurpose.EMAIL_CHANGE)
            user.pendingEmail = "thief@example.com"
            whenever(tokenValidator.findUsable("sel.ver", TokenPurpose.ACCOUNT_LOCK)).thenReturn(link)
            whenever(tokenValidator.findUnconsumedByUserId(7)).thenReturn(listOf(move, token(TokenPurpose.PASSWORD_RESET)))

            security.lockWithLink("sel.ver")

            assertThat(user.lockedAt).isEqualTo(clock.instant())
            assertThat(user.pendingEmail).isNull()
            verify(tokenFactory).consume(link)
            verify(tokenFactory).consume(move)
            verify(trustedBrowsers).forgetAll(7)
            verify(signIns).endAll(7)
            recorded(SecurityEventKind.ACCOUNT_LOCKED)
        }

        @Test
        fun `an unusable link and an account already locked do nothing`() {
            security.lockWithLink("nonsense")
            verify(users, never()).update(any())

            user.lockedAt = Instant.EPOCH
            security.lock(user)
            verify(users, never()).update(any())
        }

        @Test
        fun `an admin unlocks with a reason, corrects the address and sends a way back in`() {
            user.lockedAt = Instant.EPOCH
            user.twoFactorSince = Instant.EPOCH

            security.unlock(1, 7, "heard from them", "Fixed@Example.com")

            assertThat(user.lockedAt).isNull()
            assertThat(user.email).isEqualTo("fixed@example.com")
            verify(recovery).resetPassword("alice")
            recorded(SecurityEventKind.ACCOUNT_UNLOCKED)
            recorded(SecurityEventKind.TWO_FACTOR_RESET)
        }

        @Test
        fun `unlocking an account that is not locked, or onto a taken address, is refused`() {
            assertThrows<NotLocked> { security.unlock(1, 7, "why", null) }

            user.lockedAt = Instant.EPOCH
            whenever(users.existsByEmailAndIdNot("taken@example.com", 7)).thenReturn(true)
            assertThrows<EmailTaken> { security.unlock(1, 7, "why", "taken@example.com") }

            security.unlock(1, 7, "why", "  ")
            assertThat(user.email).isEqualTo("alice@example.com")
            verify(twoFactor, never()).erase(any())
        }
    }

    @Nested
    inner class Resetting {
        @Test
        fun `an admin resets somebody else's two-factor with a step-up`() {
            assertThrows<OwnAccount> { security.resetTwoFactorFor(7, 7, "mine") }
            assertThrows<TwoFactorOff> { security.resetTwoFactorFor(1, 7, "nothing on") }

            user.twoFactorSince = Instant.EPOCH
            security.resetTwoFactorFor(1, 7, "lost the phone")

            verify(stepUp, times(2)).require()
            verify(twoFactor).erase(7)
            assertThat(user.awaitingReenrolment).isTrue()
            verify(signIns).endAll(7)
            verify(tokenFactory).issue(user, TokenPurpose.TWO_FACTOR_REENROLMENT, AccountSecurity.REENROLMENT_TTL)
            verify(jobs).runAsync(EmailJobs.Recovery, EmailJobs.RecoveryPayload(7, "sel.ver", TokenPurpose.TWO_FACTOR_REENROLMENT))
            recorded(SecurityEventKind.TWO_FACTOR_RESET)
        }

        @Test
        fun `a new link goes only to somebody still waiting to set up, and never from their own account`() {
            assertThrows<OwnAccount> { security.resendReenrolmentLink(7, 7) }
            assertThrows<NotAwaitingReenrolment> { security.resendReenrolmentLink(1, 7) }

            user.awaitingReenrolment = true
            security.resendReenrolmentLink(1, 7)

            verify(tokenFactory).issue(user, TokenPurpose.TWO_FACTOR_REENROLMENT, AccountSecurity.REENROLMENT_TTL)
        }
    }

    @Nested
    inner class SignInsAndBrowsers {
        private val signIn = SignIn("s", 7, Instant.EPOCH, Instant.EPOCH, Browser.UNKNOWN, 0, "j", Instant.EPOCH)

        @Test
        fun `a person ends their own sign-ins only`() {
            whenever(signIns.find("s")).thenReturn(signIn)

            assertThat(security.endSignIn(8, "s")).isFalse()
            assertThat(security.endSignIn(7, "gone")).isFalse()
            assertThat(security.endSignIn(7, "s")).isTrue()
            verify(signIns).end("s")
        }

        @Test
        fun `signing out everywhere is logged`() {
            security.signOutEverywhere(7)

            verify(signIns).endAll(7)
            recorded(SecurityEventKind.SIGNED_OUT_EVERYWHERE)
        }

        @Test
        fun `the lists are read through`() {
            whenever(signIns.of(7)).thenReturn(listOf(signIn))
            whenever(trustedBrowsers.of(7)).thenReturn(emptyList())
            whenever(trustedBrowsers.forget(7, 3)).thenReturn(true)
            whenever(events.of(7, Pageable.unpaged())).thenReturn(PageImpl(emptyList()))

            assertThat(security.signInsOf(7)).containsExactly(signIn)
            assertThat(security.trustedBrowsersOf(7)).isEmpty()
            assertThat(security.forgetTrustedBrowser(7, 3)).isTrue()
            security.forgetTrustedBrowsers(7)
            assertThat(security.eventsOf(7, Pageable.unpaged())).isEmpty()
            verify(trustedBrowsers).forgetAll(7)
        }

        @Test
        fun `an admin reads where an account stands`() {
            user.awaitingReenrolment = true
            user.lockedAt = Instant.EPOCH

            assertThat(security.standingOf(7)).isEqualTo(AccountStanding(twoFactorOn = false, awaitingReenrolment = true, locked = true))
        }
    }
}

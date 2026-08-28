package net.blueshell.api.auth.domain

import net.blueshell.api.shared.model.SignupOutcome
import net.blueshell.api.domain.user.persistence.User
import net.blueshell.api.shared.enums.TokenPurpose
import net.blueshell.api.shared.job.EmailJobs
import net.blueshell.api.domain.user.application.MemberProfileService
import net.blueshell.api.domain.user.application.UserService
import net.blueshell.api.shared.job.TrackedJobDispatcher
import org.assertj.core.api.Assertions.assertThat
import org.junit.jupiter.api.Nested
import org.junit.jupiter.api.Test
import org.mockito.kotlin.eq
import org.mockito.kotlin.mock
import org.mockito.kotlin.verify
import org.mockito.kotlin.verifyNoInteractions
import org.mockito.kotlin.any
import org.mockito.kotlin.never
import org.mockito.kotlin.whenever

class RecoveryUseCasesTest {

    private val completion = mock<SignupCompletionService>()

    private val passwordRecoveryService = mock<PasswordRecoveryService>()
    private val activationService = mock<UserActivationService>()
    private val jobs = mock<TrackedJobDispatcher>()

    private val previews = mock<RecoveryEmailPreviewService>()
    private val signupTokens = mock<SignupTokenService>()
    private val users = mock<UserService>()
    private val memberProfiles = mock<MemberProfileService>()
    private val useCases =
        RecoveryUseCases(passwordRecoveryService, activationService, completion, previews, jobs)
    private val signupUseCases = SignupUseCases(signupTokens, users, memberProfiles, completion, activationService, jobs)

    @Nested
    inner class ResetPassword {


        @Test
        fun `enqueues recovery email when reset dispatch is returned`() {
            val dispatch = RecoveryDispatch(7L, "token-1", TokenPurpose.PASSWORD_RESET)
            whenever(passwordRecoveryService.requestPasswordReset("john")).thenReturn(dispatch)

            useCases.resetPassword("john")

            verify(passwordRecoveryService).requestPasswordReset("john")
            verify(jobs).runAsync(
                eq(EmailJobs.Recovery),
                eq(EmailJobs.RecoveryPayload(7L, "token-1", TokenPurpose.PASSWORD_RESET))
            )
        }

        @Test
        fun `does not enqueue email when reset dispatch is null`() {
            whenever(passwordRecoveryService.requestPasswordReset("john")).thenReturn(null)

            useCases.resetPassword("john")

            verify(passwordRecoveryService).requestPasswordReset("john")
            verifyNoInteractions(jobs)
        }
    }

    @Nested
    inner class SetPassword {


        @Test
        fun `sets password with provided token and password`() {
            useCases.setPassword("token-2", "Passw0rd!")

            verify(passwordRecoveryService).setPassword("token-2", "Passw0rd!")
        }
    }

    @Nested
    inner class UserActivate {


        @Test
        fun `activates the account and reports whether the membership started`() {
            val user = mock<User>()
            whenever(user.id).thenReturn(4L)
            whenever(activationService.activateUser("sel.ver")).thenReturn(user)
            whenever(completion.completeIfReady(4L))
                .thenReturn(SignupOutcome(emailConfirmed = true, membershipStarted = true))

            val outcome = useCases.activateUser("sel.ver")

            assertThat(outcome.membershipStarted).isTrue()
        }
    }

    @Nested
    inner class MemberActivate {


        @Test
        fun `activates member with token and credentials`() {
            useCases.activateMember("token-4", "john", "Passw0rd!")

            verify(activationService).activateMember("token-4", "john", "Passw0rd!")
        }
    }

    @Nested
    inner class ResendUserActivation {


        @Test
        fun `enqueues activation email when dispatch exists`() {
            val dispatch = RecoveryDispatch(8L, "token-5", TokenPurpose.USER_ACTIVATION)
            whenever(activationService.requestUserActivation("john")).thenReturn(dispatch)

            useCases.resendUserActivation("john")

            verify(activationService).requestUserActivation("john")
            verify(jobs).runAsync(
                eq(EmailJobs.Recovery),
                eq(EmailJobs.RecoveryPayload(8L, "token-5", TokenPurpose.USER_ACTIVATION))
            )
        }

        @Test
        fun `does not enqueue activation email when dispatch is null`() {
            whenever(activationService.requestUserActivation("john")).thenReturn(null)

            useCases.resendUserActivation("john")

            verify(activationService).requestUserActivation("john")
            verifyNoInteractions(jobs)
        }
    }

    @Nested
    inner class ResendRecoveryEmail {


        @Test
        fun `enqueues member activation email when dispatch exists`() {
            val dispatch = RecoveryDispatch(9L, "token-6", TokenPurpose.MEMBER_ACTIVATION)
            whenever(activationService.requestActivationEmail(9L)).thenReturn(dispatch)

            useCases.resendRecoveryEmail(9L, null)

            verify(activationService).requestActivationEmail(9L)
            verify(jobs).runAsync(
                eq(EmailJobs.Recovery),
                eq(EmailJobs.RecoveryPayload(9L, "token-6", TokenPurpose.MEMBER_ACTIVATION))
            )
        }

        @Test
        fun `does not enqueue member activation email when dispatch is null`() {
            whenever(activationService.requestActivationEmail(9L)).thenReturn(null)

            useCases.resendRecoveryEmail(9L, null)

            verify(activationService).requestActivationEmail(9L)
            verifyNoInteractions(jobs)
        }

        @Test
        fun `a named purpose is sent rather than whatever is outstanding`() {
            val dispatch = RecoveryDispatch(9L, "token-7", TokenPurpose.MEMBER_ACTIVATION)
            whenever(activationService.requestActivation(9L, TokenPurpose.MEMBER_ACTIVATION)).thenReturn(dispatch)

            useCases.resendRecoveryEmail(9L, TokenPurpose.MEMBER_ACTIVATION)

            verify(activationService).requestActivation(9L, TokenPurpose.MEMBER_ACTIVATION)
            verify(activationService, never()).requestActivationEmail(any())
            verify(jobs).runAsync(
                eq(EmailJobs.Recovery),
                eq(EmailJobs.RecoveryPayload(9L, "token-7", TokenPurpose.MEMBER_ACTIVATION))
            )
        }

        @Test
        fun `a named purpose for an already active account sends nothing`() {
            whenever(activationService.requestActivation(9L, TokenPurpose.USER_ACTIVATION)).thenReturn(null)

            useCases.resendRecoveryEmail(9L, TokenPurpose.USER_ACTIVATION)

            verifyNoInteractions(jobs)
        }
    }
}

package net.blueshell.api.domain.auth.application.command

import net.blueshell.api.domain.auth.application.PasswordRecoveryService
import net.blueshell.api.domain.auth.application.RecoveryDispatch
import net.blueshell.api.domain.auth.application.SignupCompletionService
import net.blueshell.api.shared.model.SignupOutcome
import net.blueshell.api.domain.auth.application.UserActivationService
import net.blueshell.api.domain.auth.command.MemberActivateCommand
import net.blueshell.api.domain.auth.command.ResendMemberActivationEmailCommand
import net.blueshell.api.domain.auth.command.ResendUserActivationCommand
import net.blueshell.api.domain.auth.command.ResetPasswordCommand
import net.blueshell.api.domain.auth.command.SetPasswordCommand
import net.blueshell.api.domain.auth.command.UserActivateCommand
import net.blueshell.api.domain.user.persistence.User
import net.blueshell.api.shared.enums.TokenPurpose
import net.blueshell.api.shared.job.EmailJobs
import net.blueshell.api.shared.job.TrackedJobDispatcher
import org.assertj.core.api.Assertions.assertThat
import org.junit.jupiter.api.Nested
import org.junit.jupiter.api.Test
import org.mockito.kotlin.eq
import org.mockito.kotlin.mock
import org.mockito.kotlin.verify
import org.mockito.kotlin.verifyNoInteractions
import org.mockito.kotlin.whenever

class RecoveryCommandHandlersTest {

    private val completion = mock<SignupCompletionService>()

    private val passwordRecoveryService = mock<PasswordRecoveryService>()
    private val activationService = mock<UserActivationService>()
    private val jobs = mock<TrackedJobDispatcher>()

    @Nested
    inner class ResetPassword {

        private val handler = ResetPasswordHandler(passwordRecoveryService, jobs)

        @Test
        fun `enqueues recovery email when reset dispatch is returned`() {
            val dispatch = RecoveryDispatch(7L, "token-1", TokenPurpose.PASSWORD_RESET)
            whenever(passwordRecoveryService.requestPasswordReset("john")).thenReturn(dispatch)

            handler.handle(ResetPasswordCommand("john"))

            verify(passwordRecoveryService).requestPasswordReset("john")
            verify(jobs).enqueue(
                eq(EmailJobs.Recovery),
                eq(EmailJobs.RecoveryPayload(7L, "token-1", TokenPurpose.PASSWORD_RESET))
            )
        }

        @Test
        fun `does not enqueue email when reset dispatch is null`() {
            whenever(passwordRecoveryService.requestPasswordReset("john")).thenReturn(null)

            handler.handle(ResetPasswordCommand("john"))

            verify(passwordRecoveryService).requestPasswordReset("john")
            verifyNoInteractions(jobs)
        }
    }

    @Nested
    inner class SetPassword {

        private val handler = SetPasswordHandler(passwordRecoveryService)

        @Test
        fun `sets password with provided token and password`() {
            handler.handle(SetPasswordCommand(token = "token-2", password = "Passw0rd!"))

            verify(passwordRecoveryService).setPassword("token-2", "Passw0rd!")
        }
    }

    @Nested
    inner class UserActivate {

        private val handler = UserActivateHandler(activationService, completion)

        @Test
        fun `activates the account and reports whether the membership started`() {
            val user = mock<User>()
            whenever(user.id).thenReturn(4L)
            whenever(activationService.activateUser("sel.ver")).thenReturn(user)
            whenever(completion.completeIfReady(4L))
                .thenReturn(SignupOutcome(emailConfirmed = true, membershipStarted = true))

            val outcome = handler.handle(UserActivateCommand("sel.ver"))

            assertThat(outcome.membershipStarted).isTrue()
        }
    }

    @Nested
    inner class MemberActivate {

        private val handler = MemberActivateHandler(activationService)

        @Test
        fun `activates member with token and credentials`() {
            handler.handle(MemberActivateCommand("token-4", "john", "Passw0rd!"))

            verify(activationService).activateMember("token-4", "john", "Passw0rd!")
        }
    }

    @Nested
    inner class ResendUserActivation {

        private val handler = ResendUserActivationHandler(activationService, jobs)

        @Test
        fun `enqueues activation email when dispatch exists`() {
            val dispatch = RecoveryDispatch(8L, "token-5", TokenPurpose.USER_ACTIVATION)
            whenever(activationService.requestUserActivation("john")).thenReturn(dispatch)

            handler.handle(ResendUserActivationCommand("john"))

            verify(activationService).requestUserActivation("john")
            verify(jobs).enqueue(
                eq(EmailJobs.Recovery),
                eq(EmailJobs.RecoveryPayload(8L, "token-5", TokenPurpose.USER_ACTIVATION))
            )
        }

        @Test
        fun `does not enqueue activation email when dispatch is null`() {
            whenever(activationService.requestUserActivation("john")).thenReturn(null)

            handler.handle(ResendUserActivationCommand("john"))

            verify(activationService).requestUserActivation("john")
            verifyNoInteractions(jobs)
        }
    }

    @Nested
    inner class ResendMemberActivationEmail {

        private val handler = ResendMemberActivationEmailHandler(activationService, jobs)

        @Test
        fun `enqueues member activation email when dispatch exists`() {
            val dispatch = RecoveryDispatch(9L, "token-6", TokenPurpose.MEMBER_ACTIVATION)
            whenever(activationService.requestActivationEmail(9L)).thenReturn(dispatch)

            handler.handle(ResendMemberActivationEmailCommand(9L))

            verify(activationService).requestActivationEmail(9L)
            verify(jobs).enqueue(
                eq(EmailJobs.Recovery),
                eq(EmailJobs.RecoveryPayload(9L, "token-6", TokenPurpose.MEMBER_ACTIVATION))
            )
        }

        @Test
        fun `does not enqueue member activation email when dispatch is null`() {
            whenever(activationService.requestActivationEmail(9L)).thenReturn(null)

            handler.handle(ResendMemberActivationEmailCommand(9L))

            verify(activationService).requestActivationEmail(9L)
            verifyNoInteractions(jobs)
        }
    }
}

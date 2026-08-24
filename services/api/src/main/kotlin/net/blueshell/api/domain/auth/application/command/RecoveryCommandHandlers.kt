package net.blueshell.api.domain.auth.application.command

import org.springframework.transaction.annotation.Transactional
import org.springframework.web.server.ResponseStatusException
import org.springframework.http.HttpStatus
import org.springframework.security.access.AccessDeniedException
import net.blueshell.api.domain.auth.command.CorrectSignupEmailCommand
import net.blueshell.api.domain.user.application.UserService
import net.blueshell.api.domain.auth.application.SignupTokenService
import net.blueshell.api.shared.model.RecoveryEmailPreview
import net.blueshell.api.shared.model.SignupSession
import net.blueshell.api.domain.auth.application.PasswordRecoveryService
import net.blueshell.api.domain.auth.application.SignupCompletionService
import net.blueshell.api.shared.model.SignupOutcome
import net.blueshell.api.domain.auth.application.UserActivationService
import net.blueshell.api.domain.auth.command.*
import net.blueshell.api.domain.user.persistence.User
import net.blueshell.api.platform.integration.email.application.service.EmailSenderService
import net.blueshell.api.shared.job.EmailJobs
import net.blueshell.api.shared.job.TrackedJobDispatcher
import net.blueshell.api.shared.command.CommandHandler
import org.springframework.stereotype.Component

@Component
class ResetPasswordHandler(
    private val passwordRecoveryService: PasswordRecoveryService,
    private val jobs: TrackedJobDispatcher
) : CommandHandler<ResetPasswordCommand, Unit> {
    override val commandType = ResetPasswordCommand::class

    override fun handle(command: ResetPasswordCommand) {
        val dispatch = passwordRecoveryService.requestPasswordReset(command.username)
        if (dispatch != null) {
            jobs.enqueue(
                EmailJobs.Recovery,
                EmailJobs.RecoveryPayload(dispatch.userId, dispatch.rawToken, dispatch.type)
            )
        }
    }
}

@Component
class SetPasswordHandler(
    private val passwordRecoveryService: PasswordRecoveryService
) : CommandHandler<SetPasswordCommand, Unit> {
    override val commandType = SetPasswordCommand::class

    override fun handle(command: SetPasswordCommand) {
        passwordRecoveryService.setPassword(command.token, command.password)
    }
}

@Component
class UserActivateHandler(
    private val activationService: UserActivationService,
    private val completion: SignupCompletionService
) : CommandHandler<UserActivateCommand, SignupOutcome> {
    override val commandType = UserActivateCommand::class

    override fun handle(command: UserActivateCommand): SignupOutcome {
        val user = activationService.activateUser(command.token)
        // The other half of the rendezvous may already be in place, in which case
        // this is the write that starts the membership (ADR-025).
        return completion.completeIfReady(user.id!!)
    }
}

@Component
class MemberActivateHandler(
    private val activationService: UserActivationService
) : CommandHandler<MemberActivateCommand, Unit> {
    override val commandType = MemberActivateCommand::class

    override fun handle(command: MemberActivateCommand) {
        activationService.activateMember(command.token, command.username, command.password)
    }
}

@Component
class ResendUserActivationHandler(
    private val activationService: UserActivationService,
    private val jobs: TrackedJobDispatcher
) : CommandHandler<ResendUserActivationCommand, Unit> {
    override val commandType = ResendUserActivationCommand::class

    override fun handle(command: ResendUserActivationCommand) {
        val dispatch = activationService.requestUserActivation(command.username)
        if (dispatch != null) {
            jobs.enqueue(
                EmailJobs.Recovery,
                EmailJobs.RecoveryPayload(dispatch.userId, dispatch.rawToken, dispatch.type)
            )
        }
    }
}

@Component
class ResendRecoveryEmailHandler(
    private val activationService: UserActivationService,
    private val jobs: TrackedJobDispatcher
) : CommandHandler<ResendRecoveryEmailCommand, Unit> {
    override val commandType = ResendRecoveryEmailCommand::class

    override fun handle(command: ResendRecoveryEmailCommand) {
        val dispatch = command.purpose
            ?.let { activationService.requestActivation(command.userId, it) }
            ?: activationService.requestActivationEmail(command.userId)
        if (dispatch != null) {
            jobs.enqueue(
                EmailJobs.Recovery,
                EmailJobs.RecoveryPayload(dispatch.userId, dispatch.rawToken, dispatch.type)
            )
        }
    }
}

@Component
class PreviewRecoveryEmailHandler(
    private val emails: EmailSenderService
) : CommandHandler<PreviewRecoveryEmailCommand, RecoveryEmailPreview> {
    override val commandType = PreviewRecoveryEmailCommand::class

    override fun handle(command: PreviewRecoveryEmailCommand): RecoveryEmailPreview =
        emails.previewRecoveryEmail(command.userId, command.purpose)
}

@Component
class IssueSignupSessionHandler(
    private val users: UserService,
    private val signupTokens: SignupTokenService
) : CommandHandler<IssueSignupSessionCommand, SignupSession> {
    override val commandType = IssueSignupSessionCommand::class

    override fun handle(command: IssueSignupSessionCommand): SignupSession =
        signupTokens.issue(users.findById(command.userId))
}

@Component
class CorrectSignupEmailHandler(
    private val signupTokens: SignupTokenService,
    private val users: UserService,
    private val activation: UserActivationService,
    private val jobs: TrackedJobDispatcher
) : CommandHandler<CorrectSignupEmailCommand, Unit> {
    override val commandType = CorrectSignupEmailCommand::class

    @Transactional
    override fun handle(command: CorrectSignupEmailCommand) {
        val account = signupTokens.resolveAccount(command.signupToken)
        if (account.user.enabled) {
            throw AccessDeniedException("A confirmed email address is changed through account settings")
        }
        if (users.existsByEmailAndIdNot(command.email, account.id)) {
            throw ResponseStatusException(HttpStatus.CONFLICT, "That email address is already in use")
        }

        account.user.email = command.email
        users.update(account.user)

        // requestUserActivation retires whatever was outstanding before issuing the
        // replacement. That matters most here: the mistyped address may be somebody
        // else's inbox, so a link already delivered there has to stop working.
        val dispatch = requireNotNull(activation.requestUserActivation(account.user.username)) {
            "Expected an activation dispatch for the unconfirmed account ${account.id}"
        }
        jobs.enqueue(
            EmailJobs.Recovery,
            EmailJobs.RecoveryPayload(dispatch.userId, dispatch.rawToken, dispatch.type)
        )
    }
}

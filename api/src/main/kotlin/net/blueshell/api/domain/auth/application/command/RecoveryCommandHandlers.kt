package net.blueshell.api.domain.auth.application.command

import net.blueshell.api.domain.auth.application.PasswordRecoveryService
import net.blueshell.api.domain.auth.application.UserActivationService
import net.blueshell.api.domain.auth.command.*
import net.blueshell.api.domain.user.persistence.User
import net.blueshell.api.platform.integration.queue.EmailJobs
import net.blueshell.api.shared.job.JobQueue
import net.blueshell.api.shared.command.CommandHandler
import org.springframework.stereotype.Component

@Component
class ResetPasswordHandler(
    private val passwordRecoveryService: PasswordRecoveryService,
    private val jobDispatcher: JobQueue
) : CommandHandler<ResetPasswordCommand, Unit> {
    override val commandType = ResetPasswordCommand::class

    override fun handle(command: ResetPasswordCommand) {
        val dispatch = passwordRecoveryService.requestPasswordReset(command.username)
        if (dispatch != null) {
            jobDispatcher.enqueue(
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
    private val activationService: UserActivationService
) : CommandHandler<UserActivateCommand, User> {
    override val commandType = UserActivateCommand::class

    override fun handle(command: UserActivateCommand): User {
        return activationService.activateUser(command.token)
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
    private val jobDispatcher: JobQueue
) : CommandHandler<ResendUserActivationCommand, Unit> {
    override val commandType = ResendUserActivationCommand::class

    override fun handle(command: ResendUserActivationCommand) {
        val dispatch = activationService.requestUserActivation(command.username)
        if (dispatch != null) {
            jobDispatcher.enqueue(
                EmailJobs.Recovery,
                EmailJobs.RecoveryPayload(dispatch.userId, dispatch.rawToken, dispatch.type)
            )
        }
    }
}

@Component
class ResendMemberActivationEmailHandler(
    private val activationService: UserActivationService,
    private val jobDispatcher: JobQueue
) : CommandHandler<ResendMemberActivationEmailCommand, Unit> {
    override val commandType = ResendMemberActivationEmailCommand::class

    override fun handle(command: ResendMemberActivationEmailCommand) {
        val dispatch = activationService.requestActivationEmail(command.userId)
        if (dispatch != null) {
            jobDispatcher.enqueue(
                EmailJobs.Recovery,
                EmailJobs.RecoveryPayload(dispatch.userId, dispatch.rawToken, dispatch.type)
            )
        }
    }
}

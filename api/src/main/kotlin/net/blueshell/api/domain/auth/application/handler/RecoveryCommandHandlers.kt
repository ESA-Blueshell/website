package net.blueshell.api.domain.auth.application.handler

import net.blueshell.api.domain.auth.application.RecoveryService
import net.blueshell.api.domain.auth.command.*
import net.blueshell.api.domain.user.persistence.User
import net.blueshell.api.shared.command.CommandHandler
import org.springframework.stereotype.Component

@Component
class ResetPasswordHandler(
    private val recoveryService: RecoveryService
) : CommandHandler<ResetPasswordCommand, Unit> {
    override val commandType = ResetPasswordCommand::class

    override fun handle(command: ResetPasswordCommand) {
        recoveryService.resetPassword(command.username)
    }
}

@Component
class SetPasswordHandler(
    private val recoveryService: RecoveryService
) : CommandHandler<SetPasswordCommand, Unit> {
    override val commandType = SetPasswordCommand::class

    override fun handle(command: SetPasswordCommand) {
        recoveryService.setPassword(command.token, command.password)
    }
}

@Component
class UserActivateHandler(
    private val recoveryService: RecoveryService
) : CommandHandler<UserActivateCommand, User> {
    override val commandType = UserActivateCommand::class

    override fun handle(command: UserActivateCommand): User {
        return recoveryService.activateUser(command.token)
    }
}

@Component
class MemberActivateHandler(
    private val recoveryService: RecoveryService
) : CommandHandler<MemberActivateCommand, Unit> {
    override val commandType = MemberActivateCommand::class

    override fun handle(command: MemberActivateCommand) {
        recoveryService.activateMember(command.token, command.username, command.password)
    }
}

@Component
class ResendUserActivationHandler(
    private val recoveryService: RecoveryService
) : CommandHandler<ResendUserActivationCommand, Unit> {
    override val commandType = ResendUserActivationCommand::class

    override fun handle(command: ResendUserActivationCommand) {
        recoveryService.resendActivation(command.username)
    }
}

@Component
class ResendMemberActivationEmailHandler(
    private val recoveryService: RecoveryService
) : CommandHandler<ResendMemberActivationEmailCommand, Unit> {
    override val commandType = ResendMemberActivationEmailCommand::class

    override fun handle(command: ResendMemberActivationEmailCommand) {
        recoveryService.resendActivationEmail(command.userId)
    }
}

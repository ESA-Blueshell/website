package net.blueshell.api.domain.auth.application.command

import net.blueshell.api.domain.auth.application.service.AuthenticationService
import net.blueshell.api.domain.auth.command.AuthenticateCommand
import net.blueshell.api.domain.auth.domain.model.AuthenticationSession
import net.blueshell.api.shared.command.CommandHandler
import org.springframework.stereotype.Component

@Component
class AuthenticateHandler(
    private val authenticationService: AuthenticationService
) : CommandHandler<AuthenticateCommand, AuthenticationSession> {

    override val commandType = AuthenticateCommand::class

    override fun handle(command: AuthenticateCommand): AuthenticationSession {
        return authenticationService.authenticate(command.username, command.password)
    }
}

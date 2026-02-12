package net.blueshell.api.domain.auth.application.handler

import net.blueshell.api.domain.auth.command.AuthenticateCommand
import net.blueshell.api.domain.auth.security.JwtTokenUtil
import net.blueshell.api.domain.auth.web.dto.response.AuthenticationResponse
import net.blueshell.api.domain.user.application.UserService
import net.blueshell.api.shared.command.CommandHandler
import org.springframework.beans.factory.annotation.Value
import org.springframework.security.authentication.AuthenticationManager
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken
import org.springframework.stereotype.Component

@Component
class AuthenticateHandler(
    private val authenticationManager: AuthenticationManager,
    private val jwtTokenUtil: JwtTokenUtil,
    private val users: UserService
) : CommandHandler<AuthenticateCommand, AuthenticationResponse> {
    @Value($$"${app.jwt.expiration}")
    private var expiration: Long = 0

    override val commandType = AuthenticateCommand::class

    override fun handle(command: AuthenticateCommand): AuthenticationResponse {
        authenticationManager.authenticate(UsernamePasswordAuthenticationToken(command.username, command.password))

        val user = users.findByUsername(command.username)
        val token = jwtTokenUtil.generateToken(user)
        val expirationTime = System.currentTimeMillis() + expiration

        return AuthenticationResponse(
            token,
            user.id!!,
            user.username,
            expirationTime,
            user.inheritedRoles as MutableSet,
            user.addressId
        )
    }
}

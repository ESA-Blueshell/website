package net.blueshell.api.domain.auth.application.command

import net.blueshell.api.domain.auth.command.AuthenticateCommand
import net.blueshell.api.domain.auth.security.JwtTokenUtil
import net.blueshell.api.domain.auth.web.dto.response.AuthenticationDTO
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
) : CommandHandler<AuthenticateCommand, AuthenticationDTO> {
    @Value($$"${app.jwt.expiration}")
    private var expiration: Long = 0

    override val commandType = AuthenticateCommand::class

    override fun handle(command: AuthenticateCommand): AuthenticationDTO {
        val username = requireNotNull(command.request.username) { "Username is required" }
        val password = requireNotNull(command.request.password) { "Password is required" }
        authenticationManager.authenticate(UsernamePasswordAuthenticationToken(username, password))

        val user = users.findByUsername(username)
        val token = jwtTokenUtil.generateToken(user)
        val expirationTime = System.currentTimeMillis() + expiration

        return AuthenticationDTO(
            token,
            user.id!!,
            user.username,
            expirationTime,
            user.inheritedRoles as MutableSet,
            user.addressId
        )
    }
}

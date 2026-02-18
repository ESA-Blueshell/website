package net.blueshell.api.infrastructure.security

import net.blueshell.api.domain.auth.domain.service.TokenGenerator
import org.springframework.beans.factory.annotation.Value
import org.springframework.stereotype.Component

@Component
class JwtTokenGenerator(
    private val jwtTokenUtil: JwtTokenUtil,
    @param:Value($$"${app.jwt.expiration}") override val expirationMs: Long
) : TokenGenerator {

    override fun generateToken(username: String): String {
        return jwtTokenUtil.generateToken(username)
    }
}

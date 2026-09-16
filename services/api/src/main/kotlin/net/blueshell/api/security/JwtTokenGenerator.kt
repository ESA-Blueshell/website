package net.blueshell.api.security

import net.blueshell.api.auth.api.TokenGenerator
import org.springframework.beans.factory.annotation.Value
import org.springframework.stereotype.Component
import java.time.Duration

@Component
class JwtTokenGenerator(
    private val jwtTokenUtil: JwtTokenUtil,
    @param:Value($$"${app.jwt.expiration}") private val expiration: Duration
) : TokenGenerator {

    override val expirationMs: Long = expiration.toMillis()

    override fun generateToken(username: String): String {
        return jwtTokenUtil.generateToken(username)
    }
}

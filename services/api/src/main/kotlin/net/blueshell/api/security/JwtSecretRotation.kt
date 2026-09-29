package net.blueshell.api.security

import io.jsonwebtoken.JwtException
import org.slf4j.LoggerFactory
import org.springframework.cloud.context.environment.EnvironmentChangeEvent
import org.springframework.context.event.EventListener
import org.springframework.core.env.Environment
import org.springframework.stereotype.Component

/** Hands a JWT secret rotated in Vault to the token signer while the api runs (api ADR-033). */
@Component
class JwtSecretRotation(
    private val tokens: JwtTokenUtil,
    private val environment: Environment,
) {
    @EventListener(EnvironmentChangeEvent::class)
    fun onChange(event: EnvironmentChangeEvent) {
        if (KEY !in event.keys) return
        try {
            tokens.rekey(environment.getProperty(KEY).orEmpty())
            log.info("JWT secret rotated")
        } catch (e: IllegalArgumentException) {
            log.error("Refused a rotated JWT secret; the secret in use stays: {}", e.message)
        } catch (e: JwtException) {
            log.error("Refused a rotated JWT secret; the secret in use stays: {}", e.message)
        }
    }

    private companion object {
        private val log = LoggerFactory.getLogger(JwtSecretRotation::class.java)
        private const val KEY = "app.jwt.secret"
    }
}

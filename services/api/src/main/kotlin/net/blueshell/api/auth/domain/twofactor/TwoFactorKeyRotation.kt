package net.blueshell.api.auth.domain.twofactor

import org.slf4j.LoggerFactory
import org.springframework.cloud.context.environment.EnvironmentChangeEvent
import org.springframework.context.event.EventListener
import org.springframework.core.env.Environment
import org.springframework.stereotype.Component

/**
 * Hands a two-factor key rotated in Vault to the cipher while the api runs (api ADR-033). A
 * malformed key is refused and the keys in use stay, since a wrong one would lock everybody out.
 */
@Component
class TwoFactorKeyRotation(
    private val cipher: SecretCipher,
    private val environment: Environment,
) {
    @EventListener(EnvironmentChangeEvent::class)
    fun onChange(event: EnvironmentChangeEvent) {
        if (event.keys.none { it.startsWith(PREFIX) }) return
        try {
            cipher.rekey(
                environment.getProperty("$PREFIX.key-id").orEmpty(),
                environment.getProperty("$PREFIX.key").orEmpty(),
                environment.getProperty("$PREFIX.retired-keys").orEmpty(),
            )
            log.info("Two-factor keys rotated")
        } catch (e: IllegalArgumentException) {
            log.error("Refused a rotated two-factor key; the keys in use stay: {}", e.message)
        }
    }

    private companion object {
        private val log = LoggerFactory.getLogger(TwoFactorKeyRotation::class.java)
        private const val PREFIX = "app.two-factor"
    }
}

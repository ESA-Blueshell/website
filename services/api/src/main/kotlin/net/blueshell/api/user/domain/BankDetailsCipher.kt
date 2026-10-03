package net.blueshell.api.user.domain

import net.blueshell.api.shared.crypto.Sealed
import net.blueshell.api.shared.crypto.SealingKeys
import org.slf4j.LoggerFactory
import org.springframework.beans.factory.annotation.Value
import org.springframework.cloud.context.environment.EnvironmentChangeEvent
import org.springframework.context.event.EventListener
import org.springframework.core.env.Environment
import org.springframework.stereotype.Component

/**
 * Seals a membership's bank details at rest (api ADR-031). Its keys fall back to the two-factor
 * keys until `app.bank-details.*` is set in Vault, so it needs no new secret to start; a key
 * rotated in Vault is taken while the api runs (api ADR-033).
 */
@Component
class BankDetailsCipher(
    @Value($$"${app.bank-details.key-id:${app.two-factor.key-id}}") currentKeyId: String,
    @Value($$"${app.bank-details.key:${app.two-factor.key}}") currentKey: String,
    @Value($$"${app.bank-details.retired-keys:${app.two-factor.retired-keys:}}") retired: String,
    private val environment: Environment,
) {
    private val keys = SealingKeys("bank detail", currentKeyId, currentKey, retired)

    fun seal(value: String): Sealed = keys.seal(value.toByteArray())

    fun open(sealed: Sealed): String = String(keys.open(sealed))

    @EventListener(EnvironmentChangeEvent::class)
    fun onChange(event: EnvironmentChangeEvent) {
        if (event.keys.none { it.startsWith("app.bank-details") || it.startsWith("app.two-factor") }) return
        try {
            keys.rekey(
                resolved("key-id"),
                resolved("key"),
                resolved("retired-keys"),
            )
            log.info("Bank detail keys rotated")
        } catch (e: IllegalArgumentException) {
            log.error("Refused a rotated bank detail key; the keys in use stay: {}", e.message)
        }
    }

    private fun resolved(name: String): String =
        environment.getProperty("app.bank-details.$name") ?: environment.getProperty("app.two-factor.$name").orEmpty()

    private companion object {
        private val log = LoggerFactory.getLogger(BankDetailsCipher::class.java)
    }
}

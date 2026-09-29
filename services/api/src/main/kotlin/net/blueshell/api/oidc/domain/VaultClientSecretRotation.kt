package net.blueshell.api.oidc.domain

import org.slf4j.LoggerFactory
import org.springframework.cloud.context.environment.EnvironmentChangeEvent
import org.springframework.context.event.EventListener
import org.springframework.core.env.Environment
import org.springframework.security.oauth2.server.authorization.client.RegisteredClient
import org.springframework.security.oauth2.server.authorization.client.RegisteredClientRepository
import org.springframework.stereotype.Component

/**
 * Re-registers the Vault OIDC client under a secret rotated in Vault while the api runs (api
 * ADR-033). The client keeps its id, which the authorizations it already holds point at. Vault's
 * own OIDC config holds the same secret; the Vault bootstrap doc gives the order to change both.
 */
@Component
class VaultClientSecretRotation(
    private val clients: RegisteredClientRepository,
    private val environment: Environment,
) {
    @EventListener(EnvironmentChangeEvent::class)
    fun onChange(event: EnvironmentChangeEvent) {
        if (KEY !in event.keys) return
        val secret = environment.getProperty(KEY).orEmpty()
        val vault = clients.findByClientId(CLIENT_ID) ?: return
        if (secret.isBlank()) {
            log.error("Refused a blank Vault OIDC client secret; the secret in use stays")
            return
        }
        clients.save(RegisteredClient.from(vault).clientSecret("{noop}$secret").build())
        log.info("Vault OIDC client secret rotated")
    }

    private companion object {
        private val log = LoggerFactory.getLogger(VaultClientSecretRotation::class.java)
        private const val KEY = "auth.clients.vault.secret"
        private const val CLIENT_ID = "vault"
    }
}

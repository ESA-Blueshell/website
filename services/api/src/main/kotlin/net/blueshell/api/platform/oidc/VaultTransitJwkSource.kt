package net.blueshell.api.platform.oidc

import com.nimbusds.jose.jwk.JWKSelector
import com.nimbusds.jose.jwk.JWKSet
import com.nimbusds.jose.jwk.RSAKey
import com.nimbusds.jose.jwk.source.JWKSource
import com.nimbusds.jose.proc.SecurityContext
import jakarta.annotation.PostConstruct
import net.blueshell.common.vault.VaultTransitClient
import org.slf4j.LoggerFactory
import org.springframework.scheduling.annotation.Scheduled
import java.util.concurrent.atomic.AtomicReference

/**
 * Caches the JWKS derived from Vault Transit public keys. Refresh runs on a
 * background schedule so `/oauth2/jwks` never blocks on (or fails because of)
 * a Vault read. If a refresh throws, the previous set is kept — a Vault blip
 * never produces a 500 on the JWKS endpoint.
 */
class VaultTransitJwkSource(
    private val client: VaultTransitClient,
    private val keyName: String,
) : JWKSource<SecurityContext> {

    private val current = AtomicReference(JWKSet(emptyList<RSAKey>()))

    @PostConstruct
    fun init() {
        try {
            refresh()
        } catch (ex: Exception) {
            log.error("Initial JWKS fetch from Vault failed; serving empty set until next refresh", ex)
        }
    }

    @Scheduled(fixedDelayString = "\${auth.transit.jwks-refresh-ms:300000}")
    fun scheduledRefresh() {
        try {
            refresh()
        } catch (ex: Exception) {
            log.warn("JWKS refresh from Vault failed; keeping previously cached set", ex)
        }
    }

    override fun get(selector: JWKSelector, context: SecurityContext?): List<com.nimbusds.jose.jwk.JWK> =
        selector.select(current.get())

    private fun refresh() {
        val publicKeys = client.readPublicKeys(keyName)
        val jwks = publicKeys.map { RSAKey.parseFromPEMEncodedObjects(it.publicKeyPem) as RSAKey }
        if (jwks.isEmpty()) error("Vault returned no public keys for transit key '$keyName'")
        current.set(JWKSet(jwks))
    }

    companion object {
        private val log = LoggerFactory.getLogger(VaultTransitJwkSource::class.java)
    }
}

package net.blueshell.api.platform.oidc

import com.nimbusds.jose.jwk.JWKSelector
import com.nimbusds.jose.jwk.JWKSet
import com.nimbusds.jose.jwk.RSAKey
import com.nimbusds.jose.jwk.source.JWKSource
import com.nimbusds.jose.proc.SecurityContext
import net.blueshell.common.vault.RestClientVaultTransitClient
import net.blueshell.common.vault.VaultTransitJwtEncoder
import org.springframework.beans.factory.annotation.Value
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty
import org.springframework.context.annotation.Bean
import org.springframework.context.annotation.Configuration
import org.springframework.context.annotation.Primary
import org.springframework.security.oauth2.jwt.JwtEncoder
import org.springframework.security.oauth2.jwt.NimbusJwtEncoder
import org.springframework.web.client.RestClient
import java.security.KeyPairGenerator
import java.security.interfaces.RSAPrivateKey
import java.security.interfaces.RSAPublicKey
import java.util.UUID

@Configuration
class OidcJwtConfig {

    @Bean
    @ConditionalOnProperty("auth.transit.enabled", havingValue = "true")
    fun vaultTransitJwtEncoder(
        @Value("\${auth.transit.vault-addr:http://vault.data-system.svc.cluster.local:8200}") vaultAddr: String,
        @Value("\${auth.transit.vault-token:}") vaultToken: String,
        @Value("\${auth.transit.key-name:api-jwt}") keyName: String,
    ): JwtEncoder {
        val restClient = RestClient.builder().baseUrl(vaultAddr).build()
        val vaultClient = RestClientVaultTransitClient(restClient, vaultToken)
        return VaultTransitJwtEncoder(vaultClient, keyName)
    }

    @Bean
    @ConditionalOnProperty("auth.transit.enabled", havingValue = "true")
    fun vaultTransitJwkSource(
        @Value("\${auth.transit.vault-addr:http://vault.data-system.svc.cluster.local:8200}") vaultAddr: String,
        @Value("\${auth.transit.vault-token:}") vaultToken: String,
        @Value("\${auth.transit.key-name:api-jwt}") keyName: String,
    ): JWKSource<SecurityContext> {
        val restClient = RestClient.builder().baseUrl(vaultAddr).build()
        val vaultClient = RestClientVaultTransitClient(restClient, vaultToken)
        return JWKSource { selector, _ ->
            val publicKeys = vaultClient.readPublicKeys(keyName)
            val jwks = publicKeys.map { vk ->
                RSAKey.parseFromPEMEncodedObjects(vk.publicKeyPem) as RSAKey
            }
            selector.select(JWKSet(jwks))
        }
    }

    @Bean
    @ConditionalOnProperty("auth.transit.enabled", havingValue = "false", matchIfMissing = true)
    fun inMemoryRsaKey(): RSAKey {
        val gen = KeyPairGenerator.getInstance("RSA")
        gen.initialize(2048)
        val pair = gen.generateKeyPair()
        return RSAKey.Builder(pair.public as RSAPublicKey)
            .privateKey(pair.private as RSAPrivateKey)
            .keyID(UUID.randomUUID().toString())
            .build()
    }

    @Bean
    @Primary
    @ConditionalOnProperty("auth.transit.enabled", havingValue = "false", matchIfMissing = true)
    fun inMemoryJwkSource(rsaKey: RSAKey): JWKSource<SecurityContext> {
        val jwkSet = JWKSet(rsaKey)
        return JWKSource<SecurityContext> { selector: JWKSelector, _: SecurityContext? ->
            selector.select(jwkSet)
        }
    }

    @Bean
    @Primary
    @ConditionalOnProperty("auth.transit.enabled", havingValue = "false", matchIfMissing = true)
    fun inMemoryJwtEncoder(jwkSource: JWKSource<SecurityContext>): JwtEncoder {
        return NimbusJwtEncoder(jwkSource)
    }
}

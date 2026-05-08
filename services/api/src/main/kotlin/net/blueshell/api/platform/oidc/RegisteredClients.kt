package net.blueshell.api.platform.oidc

import org.springframework.beans.factory.annotation.Value
import org.springframework.context.annotation.Bean
import org.springframework.context.annotation.Configuration
import org.springframework.security.oauth2.core.AuthorizationGrantType
import org.springframework.security.oauth2.core.ClientAuthenticationMethod
import org.springframework.security.oauth2.core.oidc.OidcScopes
import org.springframework.security.oauth2.server.authorization.client.InMemoryRegisteredClientRepository
import org.springframework.security.oauth2.server.authorization.client.RegisteredClient
import org.springframework.security.oauth2.server.authorization.client.RegisteredClientRepository
import org.springframework.security.oauth2.server.authorization.settings.ClientSettings
import org.springframework.security.oauth2.server.authorization.settings.TokenSettings
import java.time.Duration
import java.util.UUID

@Configuration
class RegisteredClients {

    @Bean
    fun registeredClientRepository(
        @Value("\${auth.clients.vault.secret:}") vaultClientSecret: String,
    ): RegisteredClientRepository {
        val headlamp = RegisteredClient.withId(UUID.randomUUID().toString())
            .clientId("headlamp")
            .clientAuthenticationMethod(ClientAuthenticationMethod.NONE)
            .authorizationGrantType(AuthorizationGrantType.AUTHORIZATION_CODE)
            .redirectUri("https://headlamp.esa-blueshell.nl/oidc-callback")
            .scope(OidcScopes.OPENID)
            .scope(OidcScopes.PROFILE)
            .scope(OidcScopes.EMAIL)
            .scope("groups")
            .clientSettings(
                ClientSettings.builder()
                    .requireProofKey(true)
                    .requireAuthorizationConsent(false)
                    .build()
            )
            .tokenSettings(tokenSettings())
            .build()

        val vault = RegisteredClient.withId(UUID.randomUUID().toString())
            .clientId("vault")
            .clientSecret("{noop}$vaultClientSecret")
            .clientAuthenticationMethod(ClientAuthenticationMethod.CLIENT_SECRET_BASIC)
            .authorizationGrantType(AuthorizationGrantType.AUTHORIZATION_CODE)
            .authorizationGrantType(AuthorizationGrantType.REFRESH_TOKEN)
            .redirectUri("https://vault.esa-blueshell.nl/ui/vault/auth/oidc/oidc/callback")
            .scope(OidcScopes.OPENID)
            .scope(OidcScopes.PROFILE)
            .scope(OidcScopes.EMAIL)
            .scope("groups")
            .clientSettings(
                ClientSettings.builder()
                    .requireProofKey(false)
                    .requireAuthorizationConsent(false)
                    .build()
            )
            .tokenSettings(tokenSettings())
            .build()

        return InMemoryRegisteredClientRepository(headlamp, vault)
    }

    private fun tokenSettings() = TokenSettings.builder()
        .accessTokenTimeToLive(Duration.ofMinutes(15))
        .refreshTokenTimeToLive(Duration.ofDays(7))
        .reuseRefreshTokens(false)
        .build()
}

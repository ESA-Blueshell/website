package net.blueshell.api.oidc.domain

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

private const val ACCESS_TOKEN_MINUTES = 15L
private const val REFRESH_TOKEN_DAYS = 7L

@Configuration
class RegisteredClients {
    @Bean
    fun registeredClientRepository(
        @Value("\${auth.clients.vault.secret:}") vaultClientSecret: String,
    ): RegisteredClientRepository {
        val headlamp =
            RegisteredClient
                .withId(UUID.randomUUID().toString())
                .clientId("headlamp")
                .clientAuthenticationMethod(ClientAuthenticationMethod.NONE)
                .authorizationGrantType(AuthorizationGrantType.AUTHORIZATION_CODE)
                .redirectUri("https://headlamp.esa-blueshell.nl/oidc-callback")
                .scope(OidcScopes.OPENID)
                .scope(OidcScopes.PROFILE)
                .scope(OidcScopes.EMAIL)
                .scope("groups")
                .clientSettings(
                    ClientSettings
                        .builder()
                        .requireProofKey(true)
                        .requireAuthorizationConsent(false)
                        .build(),
                ).tokenSettings(tokenSettings())
                .build()

        val vault =
            RegisteredClient
                .withId(UUID.randomUUID().toString())
                .clientId("vault")
                .clientSecret("{noop}$vaultClientSecret")
                .clientAuthenticationMethod(ClientAuthenticationMethod.CLIENT_SECRET_BASIC)
                .authorizationGrantType(AuthorizationGrantType.AUTHORIZATION_CODE)
                .authorizationGrantType(AuthorizationGrantType.REFRESH_TOKEN)
                .redirectUri("https://vault.esa-blueshell.nl/ui/vault/auth/oidc/oidc/callback")
                // `vault login -method=oidc` binds a listener on 127.0.0.1:8250 and
                // the code must return there, so the terminal flow needs a second
                // redirect_uri. Nothing off this machine can reach it, and the code
                // is exchanged by the Vault server with the client secret.
                .redirectUri("http://localhost:8250/oidc/callback")
                .scope(OidcScopes.OPENID)
                .scope(OidcScopes.PROFILE)
                .scope(OidcScopes.EMAIL)
                .scope("groups")
                .clientSettings(
                    ClientSettings
                        .builder()
                        .requireProofKey(false)
                        .requireAuthorizationConsent(false)
                        .build(),
                ).tokenSettings(tokenSettings())
                .build()

        return InMemoryRegisteredClientRepository(headlamp, vault, pingerApp())
    }

    // The desktop pinger client a member signs in. It is public like headlamp (no secret lives on
    // the member's machine) and redirects to a loopback address: the client binds an ephemeral port
    // on 127.0.0.1 and the authorization server allows any port for a loopback redirect, so the
    // registered port is a placeholder the request overrides (RFC 8252).
    //
    // It holds a rotating refresh token (reuseRefreshTokens is off) so the desktop app comes back
    // without a fresh login until the member revokes it on the security page. The authorization
    // server mints refresh tokens for a public client on this grant since Spring 7.
    private fun pingerApp(): RegisteredClient =
        RegisteredClient
            .withId(UUID.randomUUID().toString())
            .clientId("pinger-app")
            .clientName("Pinger app")
            .clientAuthenticationMethod(ClientAuthenticationMethod.NONE)
            .authorizationGrantType(AuthorizationGrantType.AUTHORIZATION_CODE)
            .authorizationGrantType(AuthorizationGrantType.REFRESH_TOKEN)
            .redirectUri("http://127.0.0.1:8991/login/oauth2/code/pinger-app")
            .scope(OidcScopes.OPENID)
            .scope(OidcScopes.PROFILE)
            .clientSettings(
                ClientSettings
                    .builder()
                    .requireProofKey(true)
                    .requireAuthorizationConsent(false)
                    .build(),
            ).tokenSettings(tokenSettings())
            .build()

    private fun tokenSettings() =
        TokenSettings
            .builder()
            .accessTokenTimeToLive(Duration.ofMinutes(ACCESS_TOKEN_MINUTES))
            .refreshTokenTimeToLive(Duration.ofDays(REFRESH_TOKEN_DAYS))
            .reuseRefreshTokens(false)
            .build()
}

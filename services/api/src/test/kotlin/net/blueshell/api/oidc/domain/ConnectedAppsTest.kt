package net.blueshell.api.oidc.domain

import org.assertj.core.api.Assertions.assertThat
import org.junit.jupiter.api.Test
import org.springframework.security.oauth2.core.AuthorizationGrantType
import org.springframework.security.oauth2.core.ClientAuthenticationMethod
import org.springframework.security.oauth2.core.OAuth2RefreshToken
import org.springframework.security.oauth2.server.authorization.InMemoryOAuth2AuthorizationService
import org.springframework.security.oauth2.server.authorization.OAuth2Authorization
import org.springframework.security.oauth2.server.authorization.OAuth2TokenType
import org.springframework.security.oauth2.server.authorization.client.InMemoryRegisteredClientRepository
import org.springframework.security.oauth2.server.authorization.client.RegisteredClient
import java.time.Instant
import java.util.UUID

class ConnectedAppsTest {
    private val pinger = registeredClient("pinger-rid", "pinger-app", "Pinger")
    private val other = registeredClient("other-rid", "other-app", "Other")
    private val nameless =
        RegisteredClient
            .withId("nameless-rid")
            .clientId("nameless-app")
            .clientAuthenticationMethod(ClientAuthenticationMethod.NONE)
            .authorizationGrantType(AuthorizationGrantType.REFRESH_TOKEN)
            .build()
    private val registeredClients = InMemoryRegisteredClientRepository(pinger, other, nameless)

    private val authorizations = IndexingOAuth2AuthorizationService(InMemoryOAuth2AuthorizationService())
    private val connectedApps = ConnectedApps(authorizations, registeredClients)

    private val day = Instant.parse("2026-01-01T00:00:00Z")

    @Test
    fun `lists an active grant as its client, resolving name and authorization time`() {
        authorizations.save(grant("ada", pinger.id))

        assertThat(connectedApps.of("ada")).containsExactly(ConnectedApp("pinger-app", "Pinger", day))
    }

    @Test
    fun `collapses several logins of one app into a single row`() {
        authorizations.save(grant("ada", pinger.id))
        authorizations.save(grant("ada", pinger.id))

        assertThat(connectedApps.of("ada").map { it.id }).containsExactly("pinger-app")
    }

    @Test
    fun `ignores a grant whose refresh token is no longer active`() {
        authorizations.save(grant("ada", pinger.id, expiresAt = Instant.now().minusSeconds(60)))

        assertThat(connectedApps.of("ada")).isEmpty()
    }

    @Test
    fun `ignores a grant for a client that is no longer registered`() {
        authorizations.save(grant("ada", "unregistered-rid"))

        assertThat(connectedApps.of("ada")).isEmpty()
    }

    @Test
    fun `falls back to the client id when the client has no name of its own`() {
        authorizations.save(grant("ada", nameless.id))

        assertThat(connectedApps.of("ada")).containsExactly(ConnectedApp("nameless-app", "nameless-app", day))
    }

    @Test
    fun `revoking an app drops only that member's grants for it`() {
        val adaPinger = grant("ada", pinger.id, refreshValue = "ada-refresh")
        val adaOther = grant("ada", other.id, refreshValue = "ada-other")
        val linusPinger = grant("linus", pinger.id, refreshValue = "linus-refresh")
        listOf(adaPinger, adaOther, linusPinger).forEach { authorizations.save(it) }

        connectedApps.revoke("ada", "pinger-app")

        assertThat(authorizations.findByToken("ada-refresh", OAuth2TokenType.REFRESH_TOKEN)).isNull()
        assertThat(authorizations.findByToken("ada-other", OAuth2TokenType.REFRESH_TOKEN)).isNotNull()
        assertThat(authorizations.findByToken("linus-refresh", OAuth2TokenType.REFRESH_TOKEN)).isNotNull()
        assertThat(connectedApps.of("linus").map { it.id }).containsExactly("pinger-app")
    }

    @Test
    fun `revoking an unknown app changes nothing`() {
        authorizations.save(grant("ada", pinger.id, refreshValue = "ada-refresh"))

        connectedApps.revoke("ada", "no-such-app")

        assertThat(authorizations.findByToken("ada-refresh", OAuth2TokenType.REFRESH_TOKEN)).isNotNull()
    }

    private fun grant(
        principal: String,
        registeredClientId: String,
        refreshValue: String = "refresh-${UUID.randomUUID()}",
        // Live by default; issued at `day` so the reported authorization time is stable.
        expiresAt: Instant = Instant.now().plusSeconds(604800),
    ): OAuth2Authorization =
        OAuth2Authorization
            .withRegisteredClient(clientWithId(registeredClientId))
            .principalName(principal)
            .authorizationGrantType(AuthorizationGrantType.REFRESH_TOKEN)
            .refreshToken(OAuth2RefreshToken(refreshValue, day, expiresAt))
            .build()

    // Only the id carries onto the authorization, so a throwaway client stands in for any
    // registered-client id, including one the repository does not know.
    private fun clientWithId(registeredClientId: String) =
        RegisteredClient
            .withId(registeredClientId)
            .clientId(registeredClientId)
            .clientAuthenticationMethod(ClientAuthenticationMethod.NONE)
            .authorizationGrantType(AuthorizationGrantType.REFRESH_TOKEN)
            .build()

    private fun registeredClient(
        id: String,
        clientId: String,
        name: String,
    ) = RegisteredClient
        .withId(id)
        .clientId(clientId)
        .clientName(name)
        .clientAuthenticationMethod(ClientAuthenticationMethod.NONE)
        .authorizationGrantType(AuthorizationGrantType.REFRESH_TOKEN)
        .build()
}

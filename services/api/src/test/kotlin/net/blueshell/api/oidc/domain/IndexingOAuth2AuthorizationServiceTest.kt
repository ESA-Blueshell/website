package net.blueshell.api.oidc.domain

import org.assertj.core.api.Assertions.assertThat
import org.junit.jupiter.api.Test
import org.springframework.security.oauth2.core.AuthorizationGrantType
import org.springframework.security.oauth2.core.ClientAuthenticationMethod
import org.springframework.security.oauth2.core.OAuth2AccessToken
import org.springframework.security.oauth2.core.OAuth2RefreshToken
import org.springframework.security.oauth2.server.authorization.InMemoryOAuth2AuthorizationService
import org.springframework.security.oauth2.server.authorization.OAuth2Authorization
import org.springframework.security.oauth2.server.authorization.OAuth2TokenType
import org.springframework.security.oauth2.server.authorization.client.RegisteredClient
import java.time.Instant
import java.util.UUID

class IndexingOAuth2AuthorizationServiceTest {
    private val delegate = InMemoryOAuth2AuthorizationService()
    private val service = IndexingOAuth2AuthorizationService(delegate)

    private val client =
        RegisteredClient
            .withId("client-id")
            .clientId("pinger-app")
            .clientAuthenticationMethod(ClientAuthenticationMethod.NONE)
            .authorizationGrantType(AuthorizationGrantType.REFRESH_TOKEN)
            .build()

    private val day = Instant.parse("2026-01-01T00:00:00Z")

    private fun authorization(
        id: String = UUID.randomUUID().toString(),
        principal: String = "ada",
        refreshValue: String? = "refresh-$id",
        issuedAt: Instant = day,
        withAccessToken: Boolean = true,
    ): OAuth2Authorization {
        val builder =
            OAuth2Authorization
                .withRegisteredClient(client)
                .id(id)
                .principalName(principal)
                .authorizationGrantType(AuthorizationGrantType.REFRESH_TOKEN)
        if (withAccessToken) {
            builder.accessToken(OAuth2AccessToken(OAuth2AccessToken.TokenType.BEARER, "access-$id", issuedAt, issuedAt.plusSeconds(900)))
        }
        refreshValue?.let { builder.refreshToken(OAuth2RefreshToken(it, issuedAt, issuedAt.plusSeconds(604800))) }
        return builder.build()
    }

    @Test
    fun `lists the authorizations a principal holds and delegates lookups`() {
        val one = authorization(principal = "ada")
        val two = authorization(principal = "ada")
        val other = authorization(principal = "linus")
        service.save(one)
        service.save(two)
        service.save(other)

        assertThat(service.findByPrincipal("ada").map { it.id }).containsExactlyInAnyOrder(one.id, two.id)
        assertThat(service.findById(one.id)).isEqualTo(one)
        assertThat(service.findByToken("refresh-${one.id}", OAuth2TokenType.REFRESH_TOKEN)?.id).isEqualTo(one.id)
    }

    @Test
    fun `an unknown principal holds nothing`() {
        assertThat(service.findByPrincipal("nobody")).isEmpty()
    }

    @Test
    fun `remove drops the authorization from the principal index and lookups`() {
        val authorization = authorization(principal = "ada")
        service.save(authorization)

        service.remove(authorization)

        assertThat(service.findByPrincipal("ada")).isEmpty()
        assertThat(service.findById(authorization.id)).isNull()
        assertThat(service.authorizedAt(authorization.id)).isNull()
    }

    @Test
    fun `the authorization time is frozen at the first grant, not moved by a later refresh`() {
        val id = "fixed"
        service.save(authorization(id = id, issuedAt = day))
        service.save(authorization(id = id, issuedAt = day.plusSeconds(3600)))

        assertThat(service.authorizedAt(id)).isEqualTo(day)
    }

    @Test
    fun `the authorization time falls back to the access token when there is no refresh token`() {
        val authorization = authorization(refreshValue = null, issuedAt = day)
        service.save(authorization)

        assertThat(service.authorizedAt(authorization.id)).isEqualTo(day)
    }

    @Test
    fun `an authorization with neither token has no known authorization time`() {
        val authorization = authorization(refreshValue = null, withAccessToken = false)
        service.save(authorization)

        assertThat(service.findByPrincipal("ada").map { it.id }).contains(authorization.id)
        assertThat(service.authorizedAt(authorization.id)).isNull()
    }

    @Test
    fun `a refresh racing a revoke cannot bring the grant back`() {
        val granted = authorization(id = "race", refreshValue = "refresh-first")
        service.save(granted)
        service.remove(granted)

        // The refresh flow rotates the token and saves the same id; the revoke must win.
        service.save(authorization(id = "race", refreshValue = "refresh-rotated"))

        assertThat(service.findByToken("refresh-rotated", OAuth2TokenType.REFRESH_TOKEN)).isNull()
        assertThat(service.findById("race")).isNull()
        assertThat(service.findByPrincipal("ada")).isEmpty()
    }

    @Test
    fun `a revoked id is hidden even if a racing save slipped it into the delegate`() {
        val granted = authorization(id = "slip", refreshValue = "refresh-slip")
        service.save(granted)
        service.remove(granted)
        // A save that read the tombstone a moment too early and landed in the delegate directly.
        delegate.save(granted)

        assertThat(service.findByToken("refresh-slip", OAuth2TokenType.REFRESH_TOKEN)).isNull()
        assertThat(service.findById("slip")).isNull()
        assertThat(service.findByPrincipal("ada")).isEmpty()
    }

    @Test
    fun `a fresh login after a revoke still works, since it is a new authorization id`() {
        val old = authorization(id = "old", refreshValue = "refresh-old")
        service.save(old)
        service.remove(old)

        service.save(authorization(id = "new", refreshValue = "refresh-new"))

        assertThat(service.findByToken("refresh-new", OAuth2TokenType.REFRESH_TOKEN)?.id).isEqualTo("new")
        assertThat(service.findByPrincipal("ada").map { it.id }).containsExactly("new")
    }
}

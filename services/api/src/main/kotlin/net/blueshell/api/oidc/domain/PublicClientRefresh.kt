package net.blueshell.api.oidc.domain

import jakarta.servlet.http.HttpServletRequest
import org.springframework.http.HttpHeaders
import org.springframework.security.authentication.AuthenticationProvider
import org.springframework.security.core.Authentication
import org.springframework.security.crypto.keygen.Base64StringKeyGenerator
import org.springframework.security.oauth2.core.AuthorizationGrantType
import org.springframework.security.oauth2.core.ClientAuthenticationMethod
import org.springframework.security.oauth2.core.OAuth2AuthenticationException
import org.springframework.security.oauth2.core.OAuth2ErrorCodes
import org.springframework.security.oauth2.core.OAuth2RefreshToken
import org.springframework.security.oauth2.core.endpoint.OAuth2ParameterNames
import org.springframework.security.oauth2.server.authorization.OAuth2TokenType
import org.springframework.security.oauth2.server.authorization.authentication.OAuth2ClientAuthenticationToken
import org.springframework.security.oauth2.server.authorization.client.RegisteredClientRepository
import org.springframework.security.oauth2.server.authorization.token.OAuth2TokenContext
import org.springframework.security.oauth2.server.authorization.token.OAuth2TokenGenerator
import org.springframework.security.web.authentication.AuthenticationConverter
import java.time.Clock
import java.util.Base64

private const val REFRESH_TOKEN_KEY_BYTES = 96
private const val TOKEN_ENDPOINT = "/oauth2/token"

/**
 * Lets a public client that holds the refresh grant (`pinger-app`) refresh with its `client_id`
 * alone. The authorization server authenticates a public client only on a PKCE code request, so
 * without this a refresh finds no client and falls through to the login redirect.
 *
 * The refresh token is the only credential, which is why it rotates on every use
 * (reuseRefreshTokens is off in RegisteredClients).
 */
internal class PublicClientRefreshConverter : AuthenticationConverter {
    override fun convert(request: HttpServletRequest): Authentication? {
        if (request.method != "POST") return null
        // Client authentication also guards introspection and revocation; a client_id alone must
        // never authenticate there, so this answers only on the token endpoint.
        if (!request.requestURI.endsWith(TOKEN_ENDPOINT)) return null
        if (request.getParameter(OAuth2ParameterNames.GRANT_TYPE) != AuthorizationGrantType.REFRESH_TOKEN.value) return null
        if (request.getHeader(HttpHeaders.AUTHORIZATION) != null) return null
        if (request.getParameter(OAuth2ParameterNames.CLIENT_SECRET) != null) return null
        val clientId = request.getParameterValues(OAuth2ParameterNames.CLIENT_ID)?.singleOrNull() ?: return null
        return OAuth2ClientAuthenticationToken(
            clientId,
            ClientAuthenticationMethod.NONE,
            null,
            mapOf(OAuth2ParameterNames.GRANT_TYPE to AuthorizationGrantType.REFRESH_TOKEN.value),
        )
    }
}

/** Authenticates what [PublicClientRefreshConverter] produced, for a public client registered to refresh. */
internal class PublicClientRefreshProvider(
    private val clients: RegisteredClientRepository,
) : AuthenticationProvider {
    override fun authenticate(authentication: Authentication): Authentication? {
        val token = authentication as OAuth2ClientAuthenticationToken
        if (token.clientAuthenticationMethod != ClientAuthenticationMethod.NONE) return null
        if (token.additionalParameters[OAuth2ParameterNames.GRANT_TYPE] != AuthorizationGrantType.REFRESH_TOKEN.value) return null
        val client = clients.findByClientId(token.principal.toString())
        if (client == null ||
            ClientAuthenticationMethod.NONE !in client.clientAuthenticationMethods ||
            AuthorizationGrantType.REFRESH_TOKEN !in client.authorizationGrantTypes
        ) {
            throw OAuth2AuthenticationException(OAuth2ErrorCodes.INVALID_CLIENT)
        }
        return OAuth2ClientAuthenticationToken(client, ClientAuthenticationMethod.NONE, null)
    }

    override fun supports(authentication: Class<*>): Boolean = OAuth2ClientAuthenticationToken::class.java.isAssignableFrom(authentication)
}

/**
 * Mints a refresh token for every client registered for the refresh grant, public ones included.
 * The stock generator withholds one from a public client on the code grant.
 */
internal class RefreshTokenGenerator(
    private val clock: Clock = Clock.systemUTC(),
) : OAuth2TokenGenerator<OAuth2RefreshToken> {
    private val keys = Base64StringKeyGenerator(Base64.getUrlEncoder().withoutPadding(), REFRESH_TOKEN_KEY_BYTES)

    override fun generate(context: OAuth2TokenContext): OAuth2RefreshToken? {
        if (context.tokenType != OAuth2TokenType.REFRESH_TOKEN) return null
        val issuedAt = clock.instant()
        return OAuth2RefreshToken(keys.generateKey(), issuedAt, issuedAt.plus(context.registeredClient.tokenSettings.refreshTokenTimeToLive))
    }
}

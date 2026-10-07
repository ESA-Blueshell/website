package net.blueshell.api.oidc.domain

import org.springframework.security.oauth2.server.authorization.OAuth2Authorization
import org.springframework.security.oauth2.server.authorization.OAuth2AuthorizationService
import org.springframework.security.oauth2.server.authorization.OAuth2TokenType
import java.time.Instant
import java.util.concurrent.ConcurrentHashMap

/**
 * An [OAuth2AuthorizationService] that keeps a resource-owner index on top of a delegate, so the
 * grants a member holds can be listed and revoked by principal. The delegate stays authoritative;
 * this only mirrors which authorization ids belong to whom.
 *
 * The index is in-memory on purpose: it shares the delegate's lifetime, and the delegate here is
 * itself in-memory (a JDBC store can't round-trip this app's principal, see AuthorizationServerConfig),
 * so both reset together on restart, which already signs every app out.
 */
class IndexingOAuth2AuthorizationService(
    private val delegate: OAuth2AuthorizationService,
) : OAuth2AuthorizationService {
    private val idsByPrincipal = ConcurrentHashMap<String, MutableSet<String>>()

    // Frozen at the first grant we see for an id, so later refresh-token rotation does not
    // keep moving the reported authorization time forward.
    private val authorizedAtById = ConcurrentHashMap<String, Instant>()

    override fun save(authorization: OAuth2Authorization) {
        delegate.save(authorization)
        idsByPrincipal.computeIfAbsent(authorization.principalName) { ConcurrentHashMap.newKeySet() }.add(authorization.id)
        grantedAt(authorization)?.let { authorizedAtById.putIfAbsent(authorization.id, it) }
    }

    override fun remove(authorization: OAuth2Authorization) {
        delegate.remove(authorization)
        idsByPrincipal[authorization.principalName]?.remove(authorization.id)
        authorizedAtById.remove(authorization.id)
    }

    override fun findById(id: String): OAuth2Authorization? = delegate.findById(id)

    override fun findByToken(
        token: String,
        tokenType: OAuth2TokenType?,
    ): OAuth2Authorization? = delegate.findByToken(token, tokenType)

    /** The authorizations held by the resource owner, newest grants first. */
    fun findByPrincipal(principalName: String): List<OAuth2Authorization> =
        idsByPrincipal[principalName].orEmpty().mapNotNull { delegate.findById(it) }

    /** When the grant behind an authorization was first established, if known. */
    fun authorizedAt(id: String): Instant? = authorizedAtById[id]

    private fun grantedAt(authorization: OAuth2Authorization): Instant? =
        (authorization.refreshToken?.token ?: authorization.accessToken?.token)?.issuedAt
}

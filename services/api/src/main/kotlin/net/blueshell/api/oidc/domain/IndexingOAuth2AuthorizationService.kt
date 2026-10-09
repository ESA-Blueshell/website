package net.blueshell.api.oidc.domain

import org.springframework.security.oauth2.server.authorization.OAuth2Authorization
import org.springframework.security.oauth2.server.authorization.OAuth2AuthorizationService
import org.springframework.security.oauth2.server.authorization.OAuth2TokenType
import java.time.Instant
import java.util.Collections
import java.util.concurrent.ConcurrentHashMap

// How many revoked ids the tombstone keeps. It caps growth over the process lifetime; the oldest
// id falls off once past it, which at worst lets a very old revoked grant be re-established — far
// beyond any live refresh window, and revokes are rare member actions.
private const val MAX_REVOKED_IDS = 10_000

/**
 * An [OAuth2AuthorizationService] that keeps a resource-owner index on top of a delegate, so the
 * grants a member holds can be listed and revoked by principal. The delegate stays authoritative;
 * this only mirrors which authorization ids belong to whom.
 *
 * The index is in-memory on purpose: it shares the delegate's lifetime, and the delegate here is
 * itself in-memory (a JDBC store can't round-trip this app's principal, see AuthorizationServerConfig),
 * so both reset together on restart, which already signs every app out.
 *
 * Revoke is final within a process lifetime: the refresh-token flow resolves a token, rotates it
 * and saves the same authorization id, so a refresh racing a revoke could otherwise re-establish
 * the just-removed grant. A tombstone of revoked ids closes that window — a revoked id is never
 * saved, listed or resolved to a token again.
 */
class IndexingOAuth2AuthorizationService(
    private val delegate: OAuth2AuthorizationService,
) : OAuth2AuthorizationService {
    private val idsByPrincipal = ConcurrentHashMap<String, MutableSet<String>>()

    // Frozen at the first grant we see for an id, so later refresh-token rotation does not
    // keep moving the reported authorization time forward.
    private val authorizedAtById = ConcurrentHashMap<String, Instant>()

    // Ids that remove() has retired, as a bounded FIFO. save() refuses them and the lookups hide
    // them, so a save racing a remove cannot bring a revoked grant back.
    private val revoked: MutableSet<String> =
        Collections.synchronizedSet(
            Collections.newSetFromMap(
                object : LinkedHashMap<String, Boolean>() {
                    override fun removeEldestEntry(eldest: Map.Entry<String, Boolean>): Boolean = size > MAX_REVOKED_IDS
                },
            ),
        )

    override fun save(authorization: OAuth2Authorization) {
        if (authorization.id in revoked) {
            // A refresh racing a revoke tried to re-establish a revoked grant; drop it instead.
            delegate.remove(authorization)
            return
        }
        delegate.save(authorization)
        idsByPrincipal.computeIfAbsent(authorization.principalName) { ConcurrentHashMap.newKeySet() }.add(authorization.id)
        grantedAt(authorization)?.let { authorizedAtById.putIfAbsent(authorization.id, it) }
    }

    override fun remove(authorization: OAuth2Authorization) {
        revoked.add(authorization.id)
        delegate.remove(authorization)
        idsByPrincipal[authorization.principalName]?.remove(authorization.id)
        authorizedAtById.remove(authorization.id)
    }

    override fun findById(id: String): OAuth2Authorization? = delegate.findById(id).takeUnless { id in revoked }

    override fun findByToken(
        token: String,
        tokenType: OAuth2TokenType?,
    ): OAuth2Authorization? = delegate.findByToken(token, tokenType)?.takeUnless { it.id in revoked }

    /** The authorizations held by the resource owner, newest grants first. */
    fun findByPrincipal(principalName: String): List<OAuth2Authorization> =
        idsByPrincipal[principalName].orEmpty().mapNotNull { findById(it) }

    /** When the grant behind an authorization was first established, if known. */
    fun authorizedAt(id: String): Instant? = authorizedAtById[id]

    private fun grantedAt(authorization: OAuth2Authorization): Instant? =
        (authorization.refreshToken?.token ?: authorization.accessToken?.token)?.issuedAt
}

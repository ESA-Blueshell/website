package net.blueshell.api.oidc.domain

import org.springframework.security.oauth2.server.authorization.OAuth2Authorization
import org.springframework.security.oauth2.server.authorization.client.RegisteredClientRepository
import org.springframework.stereotype.Service
import java.time.Instant

/** An app a member has connected: the client to revoke by, its display name and when it was authorized. */
data class ConnectedApp(
    val id: String,
    val name: String,
    val authorizedAt: Instant?,
)

/**
 * The apps a member has connected, from the authorization server's own grants. A connected app is
 * one holding a live refresh token, since that is what lets it return without the member signing it
 * in again. Revoking drops those grants, so the app's next refresh is refused.
 */
@Service
class ConnectedApps(
    private val authorizations: IndexingOAuth2AuthorizationService,
    private val registeredClients: RegisteredClientRepository,
) {
    fun of(principalName: String): List<ConnectedApp> =
        authorizations
            .findByPrincipal(principalName)
            .filter { it.refreshToken?.isActive == true }
            .mapNotNull { asConnectedApp(it) }
            .distinctBy { it.id }

    /** Removes the member's grants for one app. Scoped to the principal, so no other member is touched. */
    fun revoke(
        principalName: String,
        appId: String,
    ) {
        val client = registeredClients.findByClientId(appId) ?: return
        authorizations
            .findByPrincipal(principalName)
            .filter { it.registeredClientId == client.id }
            .forEach { authorizations.remove(it) }
    }

    private fun asConnectedApp(authorization: OAuth2Authorization): ConnectedApp? {
        val client = registeredClients.findById(authorization.registeredClientId) ?: return null
        // clientName defaults to the registered-client id (a UUID), which no member should read.
        val name = client.clientName.takeUnless { it == client.id } ?: client.clientId
        return ConnectedApp(client.clientId, name, authorizations.authorizedAt(authorization.id))
    }
}

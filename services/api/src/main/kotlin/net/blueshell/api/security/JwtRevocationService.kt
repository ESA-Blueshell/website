package net.blueshell.api.security

import org.springframework.beans.factory.annotation.Value
import org.springframework.stereotype.Component
import java.time.Duration

/**
 * Which token ids are refused, from two sources that answer different questions.
 *
 * `app.jwt.revoked-jtis` is an operator's list: it arrives with the configuration, is the same on
 * every replica and has no end, so it is held in memory. A sign-out is the other kind — it happens
 * on one replica, must be seen by all of them, and stops mattering the moment the token it names
 * would have expired anyway. That one goes to the [RevokedJtiStore].
 */
@Component
class JwtRevocationService(
    @Value($$"${app.jwt.revoked-jtis:}") revokedJtis: String,
    @param:Value($$"${app.jwt.expiration}") private val tokenLifetime: Duration,
    private val store: RevokedJtiStore,
) {
    private val configured: Set<String> =
        revokedJtis
            .split(',')
            .map { it.trim() }
            .filter { it.isNotEmpty() }
            .toSet()

    fun isRevoked(jti: String?): Boolean {
        if (jti.isNullOrBlank()) return false
        return configured.contains(jti) || store.contains(jti)
    }

    /**
     * Refuses a token id for as long as the token would have been honoured.
     *
     * A token that does not say when it expires is written down for a whole lifetime, which is the
     * longest one it could have had.
     */
    fun revoke(
        jti: String,
        expiresAtEpochMs: Long? = null,
    ) {
        if (jti.isBlank()) return
        val remaining =
            expiresAtEpochMs
                ?.let { Duration.ofMillis(it - System.currentTimeMillis()) }
                ?.takeIf { !it.isNegative && !it.isZero }
                ?: tokenLifetime
        store.add(jti, remaining)
    }
}

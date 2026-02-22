package net.blueshell.api.infrastructure.security

import org.springframework.beans.factory.annotation.Value
import org.springframework.stereotype.Component
import java.util.concurrent.ConcurrentHashMap

@Component
class JwtRevocationService(
    @param:Value($$"${app.jwt.revoked-jtis:}") revokedJtis: String
) {
    private val revoked = ConcurrentHashMap.newKeySet<String>()

    init {
        revokedJtis
            .split(',')
            .map { it.trim() }
            .filter { it.isNotEmpty() }
            .forEach { revoked.add(it) }
    }

    fun isRevoked(jti: String?): Boolean {
        return !jti.isNullOrBlank() && revoked.contains(jti)
    }

    fun revoke(jti: String) {
        if (jti.isNotBlank()) {
            revoked.add(jti)
        }
    }
}

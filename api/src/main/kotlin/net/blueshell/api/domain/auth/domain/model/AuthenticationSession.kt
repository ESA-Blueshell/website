package net.blueshell.api.domain.auth.domain.model

import net.blueshell.api.shared.enums.Role

/**
 * Represents a successful authentication session.
 * Domain model that encapsulates authentication outcome with business rules.
 */
data class AuthenticationSession(
    val token: String,
    val userId: Long,
    val username: String,
    val expiresAtEpochMs: Long,
    val roles: Set<Role>,
    val addressId: Long?
) {
    init {
        require(token.isNotBlank()) { "Token cannot be blank" }
        require(username.isNotBlank()) { "Username cannot be blank" }
        require(expiresAtEpochMs > System.currentTimeMillis()) { "Token must have future expiration" }
    }

    /**
     * Check if the session is still valid (not expired).
     */
    fun isValid(): Boolean {
        return System.currentTimeMillis() < expiresAtEpochMs
    }

    /**
     * Check if user has a specific role (including inherited roles).
     */
    fun hasRole(role: Role): Boolean {
        return roles.flatMap { it.allInheritedRoles }.any { it.matchesRole(role) }
    }

    /**
     * Get all effective authorities (roles + inherited).
     */
    fun getAllAuthorities(): Set<Role> {
        return roles.flatMap { it.allInheritedRoles }.toSet()
    }
}

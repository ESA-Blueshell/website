package net.blueshell.api.shared.security

import net.blueshell.api.shared.enums.Role
import org.springframework.security.core.GrantedAuthority
import org.springframework.security.core.authority.SimpleGrantedAuthority
import org.springframework.security.core.userdetails.UserDetails

/**
 * User principal for Spring Security authentication.
 * Moved to shared package to avoid cyclic dependencies between domain and infrastructure.
 *
 * ADR-020: Shared Kernel - Common authentication concept used across all domains.
 */
data class UserPrincipal(
    val id: Long,
    private val usernameValue: String,
    private val passwordValue: String,
    private val enabledValue: Boolean,
    val roles: Set<Role>,
    val addressId: Long?,
    val personDetailsId: Long?,
) : UserDetails {
    override fun getAuthorities(): MutableCollection<out GrantedAuthority> {
        val inherited = roles.flatMap { it.allInheritedRoles }.toSet()
        return inherited.map { SimpleGrantedAuthority(it.reprString) }.toMutableSet()
    }

    override fun getPassword(): String = passwordValue

    override fun getUsername(): String = usernameValue

    override fun isAccountNonExpired(): Boolean = true

    override fun isAccountNonLocked(): Boolean = true

    override fun isCredentialsNonExpired(): Boolean = true

    override fun isEnabled(): Boolean = enabledValue

    fun hasAuthority(role: Role): Boolean {
        return roles.flatMap { it.allInheritedRoles }.any { it.matchesRole(role) }
    }

    /**
     * Whether this is the site itself rather than a person.
     *
     * The service account exists so that records the site owns — the files it ships with — name
     * an uploader without crediting a board member with a decision they never made. Nobody signs
     * in as it, and the authentication path says so rather than relying on the account staying
     * disabled: it holds a role that inherits administrator, and a password reset must not be
     * able to turn it into a live one.
     *
     * The role it holds is the test, not the row it sits in. Anything granted SYSTEM is the site
     * speaking, whichever account carries it.
     */
    val isServiceAccount: Boolean get() = roles.contains(Role.SYSTEM)

    companion object {
        // Pinned so structurally-compatible changes to this class keep
        // deserializing across deployments. Instances are JDK-serialized into
        // Valkey both by the principal cache and by the HTTP session; without a
        // fixed id the compiler-generated serialVersionUID shifts on almost every
        // recompile, so after a deploy every stored copy fails to deserialize.
        // Bump this only on a deliberate incompatible change (the fault-tolerant
        // session serializer then discards the now-unreadable sessions and lets
        // the JWT cookie re-authenticate, rather than 500ing).
        private const val serialVersionUID: Long = 1L
    }
}

package net.blueshell.api.infrastructure.security

import net.blueshell.api.shared.enums.Role
import org.springframework.security.core.GrantedAuthority
import org.springframework.security.core.authority.SimpleGrantedAuthority
import org.springframework.security.core.userdetails.UserDetails

data class UserPrincipal(
    val id: Long,
    private val usernameValue: String,
    private val passwordValue: String,
    private val enabledValue: Boolean,
    val roles: Set<Role>,
    val addressId: Long?
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
}

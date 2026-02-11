package net.blueshell.api.domain.auth.security

import net.blueshell.api.shared.enums.Role
import net.blueshell.api.domain.user.persistence.User
import org.springframework.security.core.GrantedAuthority
import org.springframework.security.core.context.SecurityContextHolder

abstract class IdentityProvider {
    protected val principal: User?
        get() {
            val obj = SecurityContextHolder.getContext().authentication.principal

            if (obj is User) {
                return obj
            }
            return null
        }

    protected fun hasAuthority(role: Role): Boolean {
        val authentication = SecurityContextHolder.getContext().authentication
        return authentication != null && authentication.authorities.stream()
            .anyMatch { a: GrantedAuthority? -> a!!.authority == role.toString() }
    }
}
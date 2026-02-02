package net.blueshell.api.base

import lombok.extern.slf4j.Slf4j
import net.blueshell.api.common.enums.Role
import net.blueshell.api.model.User
import org.springframework.security.core.GrantedAuthority
import org.springframework.security.core.context.SecurityContextHolder

@Slf4j
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
        if (authentication == null) {
            return false
        }
        return authentication.authorities.stream()
            .anyMatch { a: GrantedAuthority? -> a!!.authority == role.toString() }
    }
}

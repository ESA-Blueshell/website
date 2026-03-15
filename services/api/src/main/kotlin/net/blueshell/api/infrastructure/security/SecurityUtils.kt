package net.blueshell.api.infrastructure.security

import net.blueshell.api.shared.enums.Role
import net.blueshell.api.shared.security.UserPrincipal
import org.springframework.security.core.Authentication
import org.springframework.security.core.context.SecurityContextHolder

object SecurityUtils {
    fun currentPrincipal(): UserPrincipal? {
        val auth = SecurityContextHolder.getContext().authentication ?: return null
        return principalFrom(auth)
    }

    fun principalFrom(authentication: Authentication?): UserPrincipal? {
        val principal = authentication?.principal
        return principal as? UserPrincipal
    }

    fun hasAuthority(role: Role): Boolean {
        return currentPrincipal()?.hasAuthority(role) == true
    }

    fun hasAuthority(authentication: Authentication?, role: Role): Boolean {
        return principalFrom(authentication)?.hasAuthority(role) == true
    }
}

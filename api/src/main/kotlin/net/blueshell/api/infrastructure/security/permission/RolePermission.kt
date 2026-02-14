package net.blueshell.api.infrastructure.security.permission

import org.springframework.security.access.PermissionEvaluator
import org.springframework.security.core.Authentication
import org.springframework.stereotype.Component
import java.io.Serializable

@Component
class RolePermission : PermissionEvaluator {

    override fun hasPermission(
        authentication: Authentication?,
        target: Any?,
        permission: Any?
    ): Boolean {
        return checkRole(authentication, permission?.toString())
    }

    override fun hasPermission(
        authentication: Authentication?,
        targetId: Serializable?,
        targetType: String?,
        permission: Any?
    ): Boolean {
        // For role checks, targetId and targetType are irrelevant
        // hasPermission(null, 'Role', 'BOARD') pattern
        return checkRole(authentication, permission?.toString())
    }

    private fun checkRole(authentication: Authentication?, role: String?): Boolean {
        if (authentication == null || role == null) return false

        return authentication.authorities?.any {
            it.authority == "ROLE_$role" || it.authority == role
        } ?: false
    }
}

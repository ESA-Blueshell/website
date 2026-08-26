package net.blueshell.api.domain.user.application.permission

import net.blueshell.api.infrastructure.security.permission.BasePermissionEvaluator

import net.blueshell.api.domain.user.application.UserService
import net.blueshell.api.domain.user.persistence.User
import net.blueshell.api.infrastructure.security.SecurityUtils
import net.blueshell.api.shared.enums.Role
import org.springframework.beans.factory.annotation.Autowired
import org.springframework.security.core.Authentication
import org.springframework.stereotype.Component

@Component
class UserPermission @Autowired constructor(service: UserService) :
    BasePermissionEvaluator<User, Long, UserService>(service) {
    override fun hasPermission(authentication: Authentication?, entity: Any?, permission: String?): Boolean {
        if (authentication == null || permission == null) {
            return false
        }

        if (entity == null) {
            return when (permission) {
                "read" -> SecurityUtils.hasAuthority(authentication, Role.BOARD)
                // Creating a user has no target to own; only the board may.
                "write" -> SecurityUtils.hasAuthority(authentication, Role.BOARD)
                "roles" -> SecurityUtils.hasAuthority(authentication, Role.ADMIN)
                "delete" -> SecurityUtils.hasAuthority(authentication, Role.BOARD)
                else -> false
            }
        }

        val user = entity as User
        val principal = SecurityUtils.principalFrom(authentication)
        val isBoard = SecurityUtils.hasAuthority(authentication, Role.BOARD)
        val isAdmin = SecurityUtils.hasAuthority(authentication, Role.ADMIN)
        return when (permission) {
            "read", "write" -> isBoard || (principal?.id == user.id)
            "delete" -> isBoard
            "roles" -> isAdmin
            "email" -> isBoard
            else -> false
        }
    }

    override fun hasPermissionId(authentication: Authentication?, id: Any?, permission: String?): Boolean {
        if (authentication == null || permission == null) {
            return false
        }

        if (id == null) return hasPermission(authentication, null, permission)

        val targetUser = service.findById(id as Long)
        return hasPermission(authentication, targetUser, permission)
    }
}
package net.blueshell.api.infrastructure.security.permission

import net.blueshell.api.domain.user.application.UserService
import net.blueshell.api.domain.user.persistence.User
import net.blueshell.api.infrastructure.security.SecurityUtils
import net.blueshell.api.platform.config.permission.BasePermissionEvaluator
import org.springframework.beans.factory.annotation.Autowired
import org.springframework.security.core.Authentication
import org.springframework.stereotype.Component

@Component
class UserPermission @Autowired constructor(service: UserService) :
    BasePermissionEvaluator<User, Long, UserService>(service) {
    override fun hasPermission(authentication: Authentication?, entity: Any?, permission: String?): Boolean {
        if (authentication == null || entity == null || permission == null) {
            return false
        }
        val user = entity as User
        val principal = SecurityUtils.principalFrom(authentication)
        return when (permission) {
            "read", "write" -> (principal?.id == user.id)
            else -> false
        }
    }

    override fun hasPermissionId(authentication: Authentication?, id: Any?, permission: String?): Boolean {
        if (authentication == null || id == null || permission == null) {
            return false
        }

        val targetUser = service.findById(id as Long)
        return hasPermission(authentication, targetUser, permission)
    }
}

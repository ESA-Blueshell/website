package net.blueshell.api.user.web.permission

import net.blueshell.api.user.persistence.User
import net.blueshell.api.user.application.UserService
import net.blueshell.api.platform.config.permission.BasePermissionEvaluator
import org.springframework.beans.factory.annotation.Autowired
import org.springframework.security.core.Authentication
import org.springframework.stereotype.Component

@Component
class UserPermission @Autowired constructor(service: UserService) :
    BasePermissionEvaluator<User, Long, UserService>(service) {
    override fun hasPermission(authentication: Authentication?, `object`: Any?, permission: String?): Boolean {
        if (authentication == null || `object` == null || permission == null) {
            return false
        }
        val user = `object` as User
        return when (permission) {
            "read", "write" -> (principal?.id == user.id)
            else -> false
        }
    }

    override fun hasPermissionId(authentication: Authentication?, targetId: Any?, permission: String?): Boolean {
        if (authentication == null || targetId == null || permission == null) {
            return false
        }

        val targetUser = service.findById(targetId as Long)
        return hasPermission(authentication, targetUser, permission)
    }
}

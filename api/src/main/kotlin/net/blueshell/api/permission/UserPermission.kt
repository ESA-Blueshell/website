package net.blueshell.api.permission

import lombok.extern.slf4j.Slf4j
import net.blueshell.api.base.BasePermissionEvaluator
import net.blueshell.api.model.User
import net.blueshell.api.service.UserService
import org.springframework.beans.factory.annotation.Autowired
import org.springframework.security.core.Authentication
import org.springframework.stereotype.Component

@Slf4j
@Component
class UserPermission @Autowired constructor(service: UserService?) :
    BasePermissionEvaluator<User?, UserService?>(service) {
    override fun hasPermission(authentication: Authentication?, `object`: Any?, permission: String?): Boolean {
        if (authentication == null || `object` == null || permission == null) {
            return false
        }
        val user = `object` as User
        val principal = getPrincipal()
        return when (permission) {
            "read", "write" -> (principal.getId() == user.getId())
            else -> false
        }
    }

    override fun hasPermissionId(authentication: Authentication?, targetId: Any?, permission: String?): Boolean {
        if (authentication == null || targetId == null || permission == null) {
            return false
        }

        val targetUser = service!!.findById(targetId as Long)
        return targetUser != null && hasPermission(authentication, targetUser, permission)
    }
}

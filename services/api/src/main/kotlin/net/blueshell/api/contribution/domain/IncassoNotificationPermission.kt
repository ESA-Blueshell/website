package net.blueshell.api.contribution.domain

import net.blueshell.api.contribution.persistence.IncassoNotification
import net.blueshell.api.security.SecurityUtils
import net.blueshell.api.security.permission.BasePermissionEvaluator
import net.blueshell.api.shared.enums.Role
import org.springframework.security.core.Authentication
import org.springframework.stereotype.Component

/** Board-only, like the payment request it sits beside. */
@Component
class IncassoNotificationPermission(service: IncassoNotificationService) :
    BasePermissionEvaluator<IncassoNotification, IncassoNotification.Id, IncassoNotificationService>(service) {

    override fun hasPermission(authentication: Authentication?, entity: Any?, permission: String?): Boolean {
        if (authentication == null || permission == null) return false
        val isBoard = SecurityUtils.hasAuthority(authentication, Role.BOARD)
        return when (permission) {
            "read", "write", "delete" -> isBoard
            else -> false
        }
    }

    override fun hasPermissionId(authentication: Authentication?, id: Any?, permission: String?): Boolean {
        if (authentication == null || permission == null) return false
        if (id == null) return hasPermission(authentication, null, permission)
        return hasPermission(authentication, service.findById(id as IncassoNotification.Id), permission)
    }
}

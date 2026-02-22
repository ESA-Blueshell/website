package net.blueshell.api.infrastructure.security.permission

import net.blueshell.api.domain.event.application.GuestService
import net.blueshell.api.domain.event.persistence.Guest
import net.blueshell.api.infrastructure.security.permission.BasePermissionEvaluator
import org.springframework.beans.factory.annotation.Autowired
import org.springframework.security.core.Authentication
import org.springframework.stereotype.Component

@Component
class GuestPermission @Autowired constructor(service: GuestService) :
    BasePermissionEvaluator<Guest, Long, GuestService>(service) {
    override fun hasPermission(
        authentication: Authentication?,
        entity: Any?,
        permission: String?
    ): Boolean {
        if (authentication == null || entity == null || permission == null) {
            return false
        }

        entity as Guest
        return when (permission) {
            "read", "write" -> true
            else -> false
        }
    }

    override fun hasPermissionId(authentication: Authentication?, id: Any?, permission: String?): Boolean {
        if (authentication == null || id == null || permission == null) {
            return false
        }
        val guest = service.findByAccessToken(id as String)
        return hasPermission(authentication, guest, permission)
    }
}

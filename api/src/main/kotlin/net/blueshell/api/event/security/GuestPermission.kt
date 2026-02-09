package net.blueshell.api.event.security

import net.blueshell.api.event.model.Guest
import net.blueshell.api.event.service.GuestService
import net.blueshell.api.platform.config.permission.BasePermissionEvaluator
import org.springframework.beans.factory.annotation.Autowired
import org.springframework.security.core.Authentication
import org.springframework.stereotype.Component

@Component
class GuestPermission @Autowired constructor(service: GuestService) :
    BasePermissionEvaluator<Guest, Long, GuestService>(service) {
    override fun hasPermission(
        authentication: Authentication?,
        targetDomainObject: Any?,
        permission: String?
    ): Boolean {
        if (authentication == null || targetDomainObject == null || permission == null) {
            return false
        }

        targetDomainObject as Guest
        return when (permission) {
            "read", "write" -> true
            else -> false
        }
    }

    override fun hasPermissionId(authentication: Authentication?, accessToken: Any?, permission: String?): Boolean {
        if (authentication == null || accessToken == null || permission == null) {
            return false
        }
        val guest = service.findByAccessToken(accessToken as String)
        return hasPermission(authentication, guest, permission)
    }
}

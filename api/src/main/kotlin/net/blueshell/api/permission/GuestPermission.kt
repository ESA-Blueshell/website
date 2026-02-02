package net.blueshell.api.permission

import net.blueshell.api.base.BasePermissionEvaluator
import net.blueshell.api.model.event.Guest
import net.blueshell.api.service.GuestService
import org.springframework.beans.factory.annotation.Autowired
import org.springframework.security.core.Authentication
import org.springframework.stereotype.Component

@Component
class GuestPermission @Autowired constructor(service: GuestService?) :
    BasePermissionEvaluator<Guest?, GuestService?>(service) {
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
            "read", "write" -> targetDomainObject != null
            else -> false
        }
    }

    override fun hasPermissionId(authentication: Authentication?, accessToken: Any?, permission: String?): Boolean {
        if (authentication == null || accessToken == null || permission == null) {
            return false
        }
        val guest = service!!.findByAccessToken(accessToken as String)
        return hasPermission(authentication, guest, permission)
    }
}

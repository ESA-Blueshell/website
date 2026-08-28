package net.blueshell.api.event.domain

import net.blueshell.api.event.persistence.EventBanner
import net.blueshell.api.security.SecurityUtils
import net.blueshell.api.security.permission.BasePermissionEvaluator
import net.blueshell.api.shared.enums.Role
import org.springframework.beans.factory.annotation.Autowired
import org.springframework.security.core.Authentication
import org.springframework.stereotype.Component

@Component
class EventBannerPermission @Autowired constructor(
    service: EventBannerService,
    private val eventPermission: EventPermission
) : BasePermissionEvaluator<EventBanner, EventBanner.Id, EventBannerService>(service) {
    override fun hasPermission(
        authentication: Authentication?,
        entity: Any?,
        permission: String?
    ): Boolean {
        if (authentication == null || permission == null) {
            return false
        }
        if (entity == null) {
            return when (permission) {
                "write" -> SecurityUtils.hasAuthority(authentication, Role.COMMITTEE)
                else -> false
            }
        }
        val target = entity as EventBanner
        return when (permission) {
            "read" -> eventPermission.hasPermission(authentication, target.event, "read")
            "write", "delete" -> eventPermission.hasPermission(authentication, target.event, "write")
            else -> false
        }
    }

    override fun hasPermissionId(authentication: Authentication?, id: Any?, permission: String?): Boolean {
        if (authentication == null || permission == null) {
            return false
        }
        if (id == null) return hasPermission(authentication, null, permission)
        val target = service.findById(id as EventBanner.Id)
        return hasPermission(authentication, target, permission)
    }
}

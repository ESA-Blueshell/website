package net.blueshell.api.event.web.permission

import net.blueshell.api.event.persistence.EventBanner
import net.blueshell.api.event.application.EventBannerService
import net.blueshell.api.platform.config.permission.BasePermissionEvaluator
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
        targetDomainObject: Any?,
        permission: String?
    ): Boolean {
        if (authentication == null || targetDomainObject == null || permission == null) {
            return false
        }
        val target = targetDomainObject as EventBanner
        return when (permission) {
            "read" -> eventPermission.hasPermission(authentication, target.event, "read")
            else -> false
        }
    }

    override fun hasPermissionId(authentication: Authentication?, targetId: Any?, permission: String?): Boolean {
        if (authentication == null || targetId == null || permission == null) {
            return false
        }
        val target = service.findById(targetId as EventBanner.Id)
        return target != null && hasPermission(authentication, target, permission)
    }
}

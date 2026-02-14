package net.blueshell.api.infrastructure.security.permission

import net.blueshell.api.domain.event.application.EventBannerService
import net.blueshell.api.domain.event.persistence.EventBanner
import net.blueshell.api.infrastructure.security.permission.BasePermissionEvaluator
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
        if (authentication == null || entity == null || permission == null) {
            return false
        }
        val target = entity as EventBanner
        return when (permission) {
            "read" -> eventPermission.hasPermission(authentication, target.event, "read")
            else -> false
        }
    }

    override fun hasPermissionId(authentication: Authentication?, id: Any?, permission: String?): Boolean {
        if (authentication == null || id == null || permission == null) {
            return false
        }
        val target = service.findById(id as EventBanner.Id)
        return hasPermission(authentication, target, permission)
    }
}

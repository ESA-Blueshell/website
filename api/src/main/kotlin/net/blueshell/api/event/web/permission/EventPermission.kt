package net.blueshell.api.event.web.permission

import net.blueshell.api.shared.enums.Role
import net.blueshell.api.event.persistence.Event
import net.blueshell.api.event.application.EventService
import net.blueshell.api.platform.config.permission.BasePermissionEvaluator
import org.springframework.beans.factory.annotation.Autowired
import org.springframework.security.core.Authentication
import org.springframework.stereotype.Component

@Component
class EventPermission @Autowired constructor(service: EventService) :
    BasePermissionEvaluator<Event, Long, EventService>(service) {
    override fun hasPermission(
        authentication: Authentication?,
        entity: Any?,
        permission: String?
    ): Boolean {
        if (authentication == null || entity == null || permission == null) {
            return false
        }
        val event = entity as Event
        val principal = principal
        return when (permission) {
            "read" -> event.approved || event.committee.hasMember(principal)
            "write" -> event.committee.hasMember(principal)
            "signUp" -> event.approved && (!event.membersOnly || hasAuthority(Role.MEMBER))
            else -> false
        }
    }

    override fun hasPermissionId(authentication: Authentication?, id: Any?, permission: String?): Boolean {
        if (authentication == null || id == null || permission == null) {
            return false
        }
        val event = service.findById(id as Long)
        return hasPermission(authentication, event, permission)
    }
}

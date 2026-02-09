package net.blueshell.api.event.security

import net.blueshell.api.shared.enums.Role
import net.blueshell.api.event.model.Event
import net.blueshell.api.event.service.EventService
import net.blueshell.api.platform.config.permission.BasePermissionEvaluator
import org.springframework.beans.factory.annotation.Autowired
import org.springframework.security.core.Authentication
import org.springframework.stereotype.Component

@Component
class EventPermission @Autowired constructor(service: EventService) :
    BasePermissionEvaluator<Event, Long, EventService>(service) {
    override fun hasPermission(
        authentication: Authentication?,
        targetDomainObject: Any?,
        permission: String?
    ): Boolean {
        if (authentication == null || targetDomainObject == null || permission == null) {
            return false
        }
        val event = targetDomainObject as Event
        val principal = principal
        return when (permission) {
            "read" -> event.approved || event.committee.hasMember(principal)
            "write" -> event.committee.hasMember(principal)
            "signUp" -> event.approved && (!event.membersOnly || hasAuthority(Role.MEMBER))
            else -> false
        }
    }

    override fun hasPermissionId(authentication: Authentication?, targetId: Any?, permission: String?): Boolean {
        if (authentication == null || targetId == null || permission == null) {
            return false
        }
        val event = service.findById(targetId as Long)
        return event != null && hasPermission(authentication, event, permission)
    }
}

package net.blueshell.api.infrastructure.security.permission

import net.blueshell.api.domain.event.application.EventService
import net.blueshell.api.domain.event.persistence.Event
import net.blueshell.api.infrastructure.security.SecurityUtils
import net.blueshell.api.infrastructure.security.permission.BasePermissionEvaluator
import net.blueshell.api.shared.enums.Role
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
        val principal = SecurityUtils.principalFrom(authentication)
        val isBoard = SecurityUtils.hasAuthority(authentication, Role.BOARD)
        return when (permission) {
            "read" -> isBoard || event.approved || event.committee.hasMember(principal?.id)
            "write" -> isBoard || event.committee.hasMember(principal?.id)
            "signUp" -> isBoard || (event.approved && (!event.membersOnly || SecurityUtils.hasAuthority(authentication, Role.MEMBER)))
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

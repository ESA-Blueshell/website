package net.blueshell.api.domain.event.application.permission

import net.blueshell.api.domain.event.application.EventService
import net.blueshell.api.domain.event.persistence.Event
import net.blueshell.api.infrastructure.security.SecurityUtils
import net.blueshell.api.infrastructure.security.permission.BasePermissionEvaluator
import net.blueshell.api.shared.enums.Role
import org.springframework.beans.factory.annotation.Autowired
import org.springframework.security.core.Authentication
import org.springframework.stereotype.Component
import java.time.Instant

@Component
class EventPermission @Autowired constructor(service: EventService) :
    BasePermissionEvaluator<Event, Long, EventService>(service) {
    override fun hasPermission(
        authentication: Authentication?,
        entity: Any?,
        permission: String?
    ): Boolean {
        if (authentication == null || permission == null) {
            return false
        }
        val isBoard = SecurityUtils.hasAuthority(authentication, Role.BOARD)
        if (entity == null) {
            return when (permission) {
                "signups" -> isBoard
                else -> false
            }
        }

        val event = entity as Event
        val principal = SecurityUtils.principalFrom(authentication)
        val isActive = event.endTime.isAfter(Instant.now())
        return when (permission) {
            // event.committee is null on orphaned events whose owning committee
            // was soft-deleted (V55). Treat that as "no committee membership
            // grants access" — only board+ can read/write/delete those.
            "read" -> isBoard || event.approved || (event.committee?.hasMember(principal?.id) == true)
            "write" -> isBoard || (event.committee?.hasMember(principal?.id) == true)
            "delete" -> isBoard || (event.committee?.hasMember(principal?.id) == true)
            "approve" -> isBoard
            "signUp" -> isBoard || (isActive && event.approved && (!event.membersOnly || SecurityUtils.hasAuthority(authentication, Role.MEMBER)))
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

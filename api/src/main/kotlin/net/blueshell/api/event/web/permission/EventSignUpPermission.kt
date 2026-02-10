package net.blueshell.api.event.web.permission

import net.blueshell.api.shared.enums.Role
import net.blueshell.api.event.persistence.EventSignUp
import net.blueshell.api.event.application.EventService
import net.blueshell.api.event.application.EventSignUpService
import net.blueshell.api.platform.config.permission.BasePermissionEvaluator
import org.springframework.beans.factory.annotation.Autowired
import org.springframework.security.core.Authentication
import org.springframework.stereotype.Component

@Component
class EventSignUpPermission @Autowired constructor(service: EventSignUpService, private val events: EventService) :
    BasePermissionEvaluator<EventSignUp, Long, EventSignUpService>(service) {
    override fun hasPermission(
        authentication: Authentication?,
        entity: Any?,
        permission: String?
    ): Boolean {
        if (authentication == null || entity == null || permission == null) {
            return false
        }

        val signUp = entity as EventSignUp
        val event = events.findById(signUp.eventId)
        val user = principal

        return when (permission) {
            "read" -> signUp.user == user || signUp.event.committee.hasMember(principal)
            "write" -> event.approved && (!event.membersOnly || hasAuthority(Role.MEMBER))
            "delete" -> (user != null && signUp.user == principal)
            else -> false
        }
    }

    override fun hasPermissionId(authentication: Authentication?, id: Any?, permission: String?): Boolean {
        if (authentication == null || id == null || permission == null) {
            return false
        }

        return hasPermission(authentication, service.findById(id as Long), permission)
    }
}

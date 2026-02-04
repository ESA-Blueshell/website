package net.blueshell.api.permission

import net.blueshell.api.base.BasePermissionEvaluator
import net.blueshell.api.common.enums.Role
import net.blueshell.api.model.event.EventSignUp
import net.blueshell.api.service.event.EventService
import net.blueshell.api.service.event.EventSignUpService
import org.springframework.beans.factory.annotation.Autowired
import org.springframework.security.core.Authentication
import org.springframework.stereotype.Component

@Component
class EventSignUpPermission @Autowired constructor(service: EventSignUpService, private val events: EventService) :
    BasePermissionEvaluator<EventSignUp, Long, EventSignUpService>(service) {
    override fun hasPermission(
        authentication: Authentication?,
        targetDomainObject: Any?,
        permission: String?
    ): Boolean {
        if (authentication == null || targetDomainObject == null || permission == null) {
            return false
        }

        val signUp = targetDomainObject as EventSignUp
        val event = events.findById(signUp.eventId)
        val user = principal

        return when (permission) {
            "read" -> signUp.user == user || signUp.event.committee.hasMember(principal)
            "write" -> event.approved && (!event.membersOnly || hasAuthority(Role.MEMBER))
            "delete" -> (user != null && signUp.user == principal)
            else -> false
        }
    }

    override fun hasPermissionId(authentication: Authentication?, targetId: Any?, permission: String?): Boolean {
        if (authentication == null || targetId == null || permission == null) {
            return false
        }

        return hasPermission(authentication, service.findById(targetId as Long), permission)
    }
}

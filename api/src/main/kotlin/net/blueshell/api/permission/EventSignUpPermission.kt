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
class EventSignUpPermission @Autowired constructor(service: EventSignUpService?, private val events: EventService) :
    BasePermissionEvaluator<EventSignUp?, EventSignUpService?>(service) {
    override fun hasPermission(
        authentication: Authentication?,
        targetDomainObject: Any?,
        permission: String?
    ): Boolean {
        if (authentication == null || targetDomainObject == null || permission == null) {
            return false
        }

        val signUp = targetDomainObject as EventSignUp
        val event = events.findById(signUp.getEventId())
        val user = getPrincipal()

        return when (permission) {
            "read" -> signUp.getUser() == user || signUp.getEvent().getCommittee().hasMember(getPrincipal())
            "write" -> event.isApproved() && (!event.isMembersOnly() || hasAuthority(Role.MEMBER))
            "delete" -> (user != null && signUp.getUser() == getPrincipal())
            else -> false
        }
    }

    override fun hasPermissionId(authentication: Authentication?, targetId: Any?, permission: String?): Boolean {
        if (authentication == null || targetId == null || permission == null) {
            return false
        }
        val signUp = service!!.findById(targetId as Long)
        return signUp != null && hasPermission(authentication, signUp, permission)
    }
}
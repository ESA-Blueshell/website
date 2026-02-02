package net.blueshell.api.permission

import lombok.extern.slf4j.Slf4j
import net.blueshell.api.base.BasePermissionEvaluator
import net.blueshell.api.common.enums.Role
import net.blueshell.api.model.event.Event
import net.blueshell.api.service.event.EventService
import org.springframework.beans.factory.annotation.Autowired
import org.springframework.security.core.Authentication
import org.springframework.stereotype.Component

@Component
@Slf4j
class EventPermission @Autowired constructor(service: EventService?) :
    BasePermissionEvaluator<Event?, EventService?>(service) {
    override fun hasPermission(
        authentication: Authentication?,
        targetDomainObject: Any?,
        permission: String?
    ): Boolean {
        if (authentication == null || targetDomainObject == null || permission == null) {
            return false
        }
        val event = targetDomainObject as Event
        val principal = getPrincipal()
        return when (permission) {
            "read" -> event.isApproved() || event.getCommittee().hasMember(principal)
            "write" -> event.getCommittee().hasMember(principal)
            "signUp" -> event.isApproved() && (!event.isMembersOnly() || hasAuthority(Role.MEMBER))
            else -> false
        }
    }

    override fun hasPermissionId(authentication: Authentication?, targetId: Any?, permission: String?): Boolean {
        if (authentication == null || targetId == null || permission == null) {
            return false
        }
        val event = service!!.findById(targetId as Long)
        return event != null && hasPermission(authentication, event, permission)
    }
}
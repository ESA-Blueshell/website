package net.blueshell.api.infrastructure.security.permission

import net.blueshell.api.domain.event.application.EventService
import net.blueshell.api.domain.event.application.EventSignUpService
import net.blueshell.api.domain.event.persistence.EventSignUp
import net.blueshell.api.infrastructure.security.SecurityUtils
import net.blueshell.api.infrastructure.security.permission.BasePermissionEvaluator
import net.blueshell.api.shared.enums.Role
import org.springframework.beans.factory.annotation.Autowired
import org.springframework.security.core.Authentication
import org.springframework.stereotype.Component
import java.time.Instant

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
        val user = SecurityUtils.principalFrom(authentication)
        val isBoard = SecurityUtils.hasAuthority(authentication, Role.BOARD)
        val isOwner = signUp.userId != null && signUp.userId == user?.id
        val isActiveEvent = event.endTime.isAfter(Instant.now())

        return when (permission) {
            "read" -> isBoard || signUp.userId == user?.id || signUp.event.committee.hasMember(user?.id)
            "write" -> isBoard || (isOwner && isActiveEvent)
            "delete" -> isBoard || (isOwner && isActiveEvent)
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

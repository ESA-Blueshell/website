package net.blueshell.api.committee.web.permission

import net.blueshell.api.committee.persistence.Committee
import net.blueshell.api.committee.application.CommitteeService
import net.blueshell.api.platform.config.permission.BasePermissionEvaluator
import org.springframework.beans.factory.annotation.Autowired
import org.springframework.security.core.Authentication
import org.springframework.stereotype.Component

@Component
class CommitteePermission @Autowired constructor(service: CommitteeService) :
    BasePermissionEvaluator<Committee, Long, CommitteeService>(service) {
    override fun hasPermission(
        authentication: Authentication?,
        targetDomainObject: Any?,
        permission: String?
    ): Boolean {
        if (authentication == null || targetDomainObject == null || permission == null) {
            return false
        }
        val committee = targetDomainObject as Committee
        return when (permission) {
            "read" -> true
            "events" -> committee.hasMember(principal)
            else -> false
        }
    }

    override fun hasPermissionId(authentication: Authentication?, targetId: Any?, permission: String?): Boolean {
        if (authentication == null || targetId == null || permission == null) {
            return false
        }
        val committee = service.findById(targetId as Long)
        return committee != null && hasPermission(authentication, committee, permission)
    }
}
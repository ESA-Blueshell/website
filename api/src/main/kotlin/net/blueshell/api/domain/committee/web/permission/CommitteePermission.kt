package net.blueshell.api.domain.committee.web.permission

import net.blueshell.api.domain.committee.application.CommitteeService
import net.blueshell.api.domain.committee.persistence.Committee
import net.blueshell.api.platform.config.permission.BasePermissionEvaluator
import org.springframework.beans.factory.annotation.Autowired
import org.springframework.security.core.Authentication
import org.springframework.stereotype.Component

@Component
class CommitteePermission @Autowired constructor(service: CommitteeService) :
    BasePermissionEvaluator<Committee, Long, CommitteeService>(service) {
    override fun hasPermission(
        authentication: Authentication?,
        entity: Any?,
        permission: String?
    ): Boolean {
        if (authentication == null || entity == null || permission == null) {
            return false
        }
        val committee = entity as Committee
        return when (permission) {
            "read" -> true
            "events" -> committee.hasMember(principal)
            else -> false
        }
    }

    override fun hasPermissionId(authentication: Authentication?, id: Any?, permission: String?): Boolean {
        if (authentication == null || id == null || permission == null) {
            return false
        }
        val committee = service.findById(id as Long)
        return hasPermission(authentication, committee, permission)
    }
}
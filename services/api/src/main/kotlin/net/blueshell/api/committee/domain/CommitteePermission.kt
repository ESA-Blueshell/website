package net.blueshell.api.committee.domain

import net.blueshell.api.committee.api.CommitteeService
import net.blueshell.api.committee.persistence.Committee
import net.blueshell.api.security.SecurityUtils
import net.blueshell.api.security.permission.BasePermissionEvaluator
import net.blueshell.api.shared.enums.Role
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
        if (authentication == null || permission == null) {
            return false
        }
        val isBoard = SecurityUtils.hasAuthority(authentication, Role.BOARD)
        if (entity == null) {
            return when (permission) {
                "write", "delete" -> isBoard
                "read" -> true
                else -> false
            }
        }

        val committee = entity as Committee
        val principal = SecurityUtils.principalFrom(authentication)
        return when (permission) {
            "read" -> true
            "events" -> isBoard || committee.hasMember(principal?.id)
            "write" -> isBoard
            "delete" -> isBoard
            else -> false
        }
    }

    override fun hasPermissionId(authentication: Authentication?, id: Any?, permission: String?): Boolean {
        if (authentication == null || permission == null) {
            return false
        }
        if (id == null) return hasPermission(authentication, null, permission)
        val committee = service.findById(id as Long)
        return hasPermission(authentication, committee, permission)
    }
}

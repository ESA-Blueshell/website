package net.blueshell.api.infrastructure.security.permission

import net.blueshell.api.domain.user.application.MembershipService
import net.blueshell.api.domain.user.persistence.Membership
import net.blueshell.api.infrastructure.security.SecurityUtils
import net.blueshell.api.shared.enums.Role
import org.springframework.beans.factory.annotation.Autowired
import org.springframework.security.core.Authentication
import org.springframework.stereotype.Component

@Component
class MembershipPermission @Autowired constructor(service: MembershipService) :
    BasePermissionEvaluator<Membership, Long, MembershipService>(service) {

    override fun hasPermission(authentication: Authentication?, entity: Any?, permission: String?): Boolean {
        if (authentication == null || permission == null) {
            return false
        }
        val isBoard = SecurityUtils.hasAuthority(authentication, Role.BOARD)
        if (entity == null) {
            return when (permission) {
                "read", "write", "delete" -> isBoard
                else -> false
            }
        }

        val membership = entity as Membership
        val principal = SecurityUtils.principalFrom(authentication)

        return when (permission) {
            "read" -> isBoard || principal?.id == membership.userId
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

        val membership = service.findById(id as Long)
        return hasPermission(authentication, membership, permission)
    }
}

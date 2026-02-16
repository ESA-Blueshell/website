package net.blueshell.api.infrastructure.security.permission

import net.blueshell.api.domain.membership.application.MembershipService
import net.blueshell.api.domain.membership.persistence.Membership
import net.blueshell.api.infrastructure.security.SecurityUtils
import net.blueshell.api.shared.enums.Role
import org.springframework.beans.factory.annotation.Autowired
import org.springframework.security.core.Authentication
import org.springframework.stereotype.Component

@Component
class MembershipPermission @Autowired constructor(service: MembershipService) :
    BasePermissionEvaluator<Membership, Long, MembershipService>(service) {

    override fun hasPermission(authentication: Authentication?, entity: Any?, permission: String?): Boolean {
        if (authentication == null || entity == null || permission == null) {
            return false
        }

        val membership = entity as Membership
        val principal = SecurityUtils.principalFrom(authentication)
        val isBoard = SecurityUtils.hasAuthority(authentication, Role.BOARD)

        return when (permission) {
            "read", "write" -> isBoard || principal?.id == membership.user?.id
            else -> false
        }
    }

    override fun hasPermissionId(authentication: Authentication?, id: Any?, permission: String?): Boolean {
        if (authentication == null || id == null || permission == null) {
            return false
        }

        val membership = service.findById(id as Long)
        return hasPermission(authentication, membership, permission)
    }
}

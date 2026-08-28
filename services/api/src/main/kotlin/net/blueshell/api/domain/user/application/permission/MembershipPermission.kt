package net.blueshell.api.domain.user.application.permission

import net.blueshell.api.security.permission.BasePermissionEvaluator

import net.blueshell.api.domain.user.application.MembershipService
import net.blueshell.api.domain.user.persistence.Membership
import net.blueshell.api.security.SecurityUtils
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
        val isAdmin = SecurityUtils.hasAuthority(authentication, Role.ADMIN)
        if (entity == null) {
            return when (permission) {
                "read", "write", "delete" -> isBoard
                // Restoring a soft-deleted membership and viewing the deleted set are
                // ADMIN-only by design (#383) — deliberately stricter than user restore,
                // which sits at BOARD.
                "restore", "read-deleted" -> isAdmin
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

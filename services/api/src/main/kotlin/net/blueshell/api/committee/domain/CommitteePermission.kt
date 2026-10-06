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
class CommitteePermission
    @Autowired
    constructor(
        private val service: CommitteeService,
    ) : BasePermissionEvaluator<Committee, Long>() {
        override fun hasPermission(
            authentication: Authentication?,
            entity: Any?,
            permission: String?,
        ): Boolean {
            if (authentication == null || permission == null) {
                return false
            }
            val isBoard = SecurityUtils.hasAuthority(authentication, Role.BOARD)
            // Deleting hands a committee's events to another one, so it is an admin's; archiving stays the board's.
            val isAdmin = SecurityUtils.hasAuthority(authentication, Role.ADMIN)
            if (entity == null) {
                return when (permission) {
                    "write" -> isBoard
                    "delete" -> isAdmin
                    "read" -> true
                    else -> false
                }
            }

            val committee = entity as Committee
            val principal = SecurityUtils.principalFrom(authentication)
            return when (permission) {
                "read" -> true
                "events", "page" -> isBoard || committee.hasMember(principal?.id)
                "write" -> isBoard
                "delete" -> isAdmin
                else -> false
            }
        }

        override fun hasPermissionId(
            authentication: Authentication?,
            id: Any?,
            permission: String?,
        ): Boolean {
            if (authentication == null || permission == null) {
                return false
            }
            if (id == null) return hasPermission(authentication, null, permission)
            val committee = service.findById(id as Long)
            return hasPermission(authentication, committee, permission)
        }
    }

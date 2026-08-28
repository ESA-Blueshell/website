package net.blueshell.api.email.domain

import net.blueshell.api.security.permission.BasePermissionEvaluator

import net.blueshell.api.security.SecurityUtils
import net.blueshell.api.email.persistence.Email
import net.blueshell.api.shared.enums.Role
import org.springframework.beans.factory.annotation.Autowired
import org.springframework.security.core.Authentication
import org.springframework.stereotype.Component

@Component
class EmailPermission @Autowired constructor(service: EmailService) :
    BasePermissionEvaluator<Email, Long, EmailService>(service) {

    override fun hasPermission(authentication: Authentication?, entity: Any?, permission: String?): Boolean {
        if (authentication == null || permission == null) return false
        val isAdmin = SecurityUtils.hasAuthority(authentication, Role.ADMIN)
        val isBoard = SecurityUtils.hasAuthority(authentication, Role.BOARD)
        return when (permission) {
            // The board fields "I never got the email", so it can see what was sent, read a
            // delivery back and send a failed one again.
            "read", "retry" -> isAdmin || isBoard
            // Changing or removing outbox rows is rewriting the delivery record itself.
            "write", "delete" -> isAdmin
            else -> false
        }
    }

    override fun hasPermissionId(authentication: Authentication?, id: Any?, permission: String?): Boolean {
        if (authentication == null || permission == null) return false
        if (id == null) return hasPermission(authentication, null, permission)
        val entity = service.findById(id as Long)
        return hasPermission(authentication, entity, permission)
    }
}

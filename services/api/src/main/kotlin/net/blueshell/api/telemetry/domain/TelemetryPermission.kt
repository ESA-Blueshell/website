package net.blueshell.api.telemetry.domain

import net.blueshell.api.infrastructure.security.permission.BasePermissionEvaluator

import net.blueshell.api.telemetry.persistence.Telemetry
import net.blueshell.api.infrastructure.security.SecurityUtils
import net.blueshell.api.shared.enums.Role
import org.springframework.beans.factory.annotation.Autowired
import org.springframework.security.core.Authentication
import org.springframework.stereotype.Component

@Component
class TelemetryPermission @Autowired constructor(service: TelemetryService) :
    BasePermissionEvaluator<Telemetry, Long, TelemetryService>(service) {
    override fun hasPermission(authentication: Authentication?, entity: Any?, permission: String?): Boolean {
        if (authentication == null || permission == null) {
            return false
        }

        if (entity == null) {
            return when (permission) {
                "write", "delete" -> SecurityUtils.hasAuthority(authentication, Role.BOARD)
                else -> false
            }
        }

        val isBoard = SecurityUtils.hasAuthority(authentication, Role.BOARD)
        return when (permission) {
            "read", "write", "delete" -> isBoard
            else -> false
        }
    }

    override fun hasPermissionId(authentication: Authentication?, id: Any?, permission: String?): Boolean {
        if (authentication == null || permission == null) {
            return false
        }
        if (id == null) return hasPermission(authentication, null, permission)
        val entity = service.findById(id as Long)
        return hasPermission(authentication, entity, permission)
    }
}

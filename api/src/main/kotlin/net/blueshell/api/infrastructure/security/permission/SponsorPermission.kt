package net.blueshell.api.infrastructure.security.permission

import net.blueshell.api.domain.sponsor.application.SponsorService
import net.blueshell.api.domain.sponsor.persistence.Sponsor
import net.blueshell.api.infrastructure.security.SecurityUtils
import net.blueshell.api.shared.enums.Role
import org.springframework.beans.factory.annotation.Autowired
import org.springframework.security.core.Authentication
import org.springframework.stereotype.Component

@Component
class SponsorPermission @Autowired constructor(service: SponsorService) :
    BasePermissionEvaluator<Sponsor, Long, SponsorService>(service) {
    override fun hasPermission(authentication: Authentication?, entity: Any?, permission: String?): Boolean {
        if (authentication == null || permission == null) {
            return false
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
        val sponsor = service.findById(id as Long)
        return hasPermission(authentication, sponsor, permission)
    }
}

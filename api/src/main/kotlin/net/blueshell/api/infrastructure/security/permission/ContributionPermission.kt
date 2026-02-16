package net.blueshell.api.infrastructure.security.permission

import net.blueshell.api.domain.contribution.application.ContributionService
import net.blueshell.api.domain.contribution.persistence.Contribution
import net.blueshell.api.infrastructure.security.SecurityUtils
import net.blueshell.api.infrastructure.security.permission.BasePermissionEvaluator
import net.blueshell.api.shared.enums.Role
import org.springframework.beans.factory.annotation.Autowired
import org.springframework.security.core.Authentication
import org.springframework.stereotype.Component

@Component
class ContributionPermission @Autowired constructor(service: ContributionService) :
    BasePermissionEvaluator<Contribution, Contribution.Id, ContributionService>(service) {
    override fun hasPermission(authentication: Authentication?, entity: Any?, permission: String?): Boolean {
        if (authentication == null || entity == null || permission == null) {
            return false
        }
        val contribution = entity as Contribution
        val principal = SecurityUtils.principalFrom(authentication)
        val isBoard = SecurityUtils.hasAuthority(authentication, Role.BOARD)
        return when (permission) {
            "read" -> isBoard || (principal?.id == contribution.userId)
            else -> false
        }
    }

    override fun hasPermissionId(authentication: Authentication?, id: Any?, permission: String?): Boolean {
        if (authentication == null || id == null || permission == null) {
            return false
        }

        val targetContribution = service.findById(id as Contribution.Id)
        return hasPermission(authentication, targetContribution, permission)
    }
}

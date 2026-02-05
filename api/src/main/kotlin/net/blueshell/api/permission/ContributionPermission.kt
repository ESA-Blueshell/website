package net.blueshell.api.permission

import net.blueshell.api.permission.BasePermissionEvaluator
import net.blueshell.api.model.contribution.Contribution
import net.blueshell.api.model.contribution.ContributionId
import net.blueshell.api.service.contribution.ContributionService
import org.springframework.beans.factory.annotation.Autowired
import org.springframework.security.core.Authentication
import org.springframework.stereotype.Component

@Component
class ContributionPermission @Autowired constructor(service: ContributionService) :
    BasePermissionEvaluator<Contribution, ContributionId, ContributionService>(service) {
    override fun hasPermission(authentication: Authentication?, `object`: Any?, permission: String?): Boolean {
        if (authentication == null || `object` == null || permission == null) {
            return false
        }
        val contribution = `object` as Contribution
        return when (permission) {
            "read" -> (principal?.id == contribution.userId)
            else -> false
        }
    }

    override fun hasPermissionId(authentication: Authentication?, targetId: Any?, permission: String?): Boolean {
        if (authentication == null || targetId == null || permission == null) {
            return false
        }

        val targetContribution = service.findById(targetId as ContributionId)
        return targetContribution != null && hasPermission(authentication, targetContribution, permission)
    }
}

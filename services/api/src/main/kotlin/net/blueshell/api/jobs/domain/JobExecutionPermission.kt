package net.blueshell.api.jobs.domain

import net.blueshell.api.security.permission.BasePermissionEvaluator

import net.blueshell.api.security.SecurityUtils
import net.blueshell.api.jobs.persistence.JobExecution
import net.blueshell.api.shared.enums.Role
import org.springframework.beans.factory.annotation.Autowired
import org.springframework.security.core.Authentication
import org.springframework.stereotype.Component

@Component
class JobExecutionPermission @Autowired constructor(service: JobExecutionService) :
    BasePermissionEvaluator<JobExecution, Long, JobExecutionService>(service) {
    override fun hasPermission(authentication: Authentication?, entity: Any?, permission: String?): Boolean {
        if (authentication == null || permission == null) {
            return false
        }

        val isAdmin = SecurityUtils.hasAuthority(authentication, Role.ADMIN)
        return when (permission) {
            "read", "retry", "write", "delete" -> isAdmin
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

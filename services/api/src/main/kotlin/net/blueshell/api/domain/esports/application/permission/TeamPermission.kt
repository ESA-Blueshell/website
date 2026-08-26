package net.blueshell.api.domain.esports.application.permission

import net.blueshell.api.domain.esports.application.TeamService
import net.blueshell.api.domain.esports.persistence.Team
import net.blueshell.api.infrastructure.security.SecurityUtils
import net.blueshell.api.infrastructure.security.permission.BasePermissionEvaluator
import net.blueshell.api.shared.enums.Role
import org.springframework.security.core.Authentication
import org.springframework.stereotype.Component

/**
 * Who may change esports.
 *
 * One evaluator for the whole of it — seasons, teams and rosters are edited together by the
 * same people, and splitting them into three would say the same thing three times. Reading is
 * public because the teams are on the public pages.
 */
@Component
class TeamPermission(service: TeamService) :
    BasePermissionEvaluator<Team, Long, TeamService>(service) {
    override fun hasPermission(authentication: Authentication?, entity: Any?, permission: String?): Boolean {
        if (authentication == null || permission == null) return false
        return when (permission) {
            "read" -> true
            "write", "delete" -> SecurityUtils.hasAuthority(authentication, Role.BOARD)
            else -> false
        }
    }

    override fun hasPermissionId(authentication: Authentication?, id: Any?, permission: String?): Boolean =
        hasPermission(authentication, null, permission)
}

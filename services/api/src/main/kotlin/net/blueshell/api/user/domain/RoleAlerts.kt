package net.blueshell.api.user.domain

import net.blueshell.api.alerts.api.AlertAudience
import net.blueshell.api.alerts.api.AlertKind
import net.blueshell.api.alerts.api.AlertSource
import net.blueshell.api.alerts.api.RaisedAlert
import net.blueshell.api.user.persistence.UserRepository
import org.springframework.stereotype.Component
import org.springframework.transaction.annotation.Transactional

/** A granted role that waits on its holder setting up two-factor, one alert per person, for an admin. */
@Component
class RoleAlerts(
    private val users: UserRepository,
) : AlertSource {
    override val audience = AlertAudience.ADMIN

    @Transactional(readOnly = true)
    override fun raised(): List<RaisedAlert> =
        users.findHoldingWithoutTwoFactor(GrantedRoles.ASSIGNABLE).map { user ->
            RaisedAlert(
                key = "role-awaiting-two-factor:${user.id}",
                kind = AlertKind.ROLE_AWAITING_TWO_FACTOR,
                subjectId = user.id,
                subjectLabel = user.username,
                count = user.dormantRoles.size.toLong(),
                since = null,
            )
        }
}

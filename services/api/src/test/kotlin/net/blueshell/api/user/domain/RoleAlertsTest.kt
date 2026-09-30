package net.blueshell.api.user.domain

import net.blueshell.api.alerts.api.AlertAudience
import net.blueshell.api.alerts.api.AlertKind
import net.blueshell.api.alerts.api.RaisedAlert
import net.blueshell.api.shared.enums.Role
import net.blueshell.api.testsupport.Entities
import net.blueshell.api.user.persistence.UserRepository
import org.assertj.core.api.Assertions.assertThat
import org.junit.jupiter.api.Test
import org.mockito.kotlin.mock
import org.mockito.kotlin.whenever

class RoleAlertsTest {
    private val users: UserRepository = mock()
    private val alerts = RoleAlerts(users)

    @Test
    fun `each person whose granted role waits on two-factor raises an admin alert`() {
        whenever(users.findHoldingWithoutTwoFactor(GrantedRoles.ASSIGNABLE))
            .thenReturn(listOf(Entities.user(id = 5, username = "ada", roles = setOf(Role.BOARD, Role.TREASURER, Role.MEMBER))))

        assertThat(alerts.audience).isEqualTo(AlertAudience.ADMIN)
        assertThat(alerts.raised())
            .containsExactly(RaisedAlert("role-awaiting-two-factor:5", AlertKind.ROLE_AWAITING_TWO_FACTOR, 5, "ada", 2, null))
    }
}

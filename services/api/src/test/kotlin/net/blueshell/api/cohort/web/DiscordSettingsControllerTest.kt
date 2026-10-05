package net.blueshell.api.cohort.web

import net.blueshell.api.cohort.domain.ServerCohortRole
import net.blueshell.api.cohort.domain.ServerCohortRoles
import net.blueshell.api.cohort.persistence.CohortType
import org.assertj.core.api.Assertions.assertThat
import org.junit.jupiter.api.Test
import org.mockito.kotlin.mock
import org.mockito.kotlin.whenever

class DiscordSettingsControllerTest {
    private val roles: ServerCohortRoles = mock()
    private val members =
        ServerCohortRole("CURRENT_MEMBERS", CohortType.CURRENT_MEMBERS, "Members", "111", "Blueshell's Finest", emptyList())

    @Test
    fun `lists the server-wide cohorts and sets the role one follows`() {
        whenever(roles.read()).thenReturn(listOf(members))
        whenever(roles.set("CURRENT_MEMBERS", "111", false)).thenReturn(listOf(members))
        val controller = DiscordSettingsController(roles)

        assertThat(controller.listServerCohortRoles()).containsExactly(members)
        assertThat(controller.setServerCohortRole("CURRENT_MEMBERS", ServerCohortRoleRequest(roleId = "111"))).containsExactly(members)
    }
}

package net.blueshell.api.cohort.web

import net.blueshell.api.cohort.domain.DiscordChoice
import net.blueshell.api.cohort.domain.DiscordPlace
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
    private val place = DiscordPlace(true, "111", "Blueshell's Finest", emptyList())

    @Test
    fun `lists the server-wide cohorts, and reads and sets one's role and channels`() {
        whenever(roles.read()).thenReturn(listOf(members))
        whenever(roles.place("CURRENT_MEMBERS")).thenReturn(place)
        whenever(roles.apply("CURRENT_MEMBERS", DiscordChoice(roleId = "111", channelIds = listOf("900")))).thenReturn(place)
        val controller = DiscordSettingsController(roles)

        assertThat(controller.listServerCohortRoles()).containsExactly(members)
        assertThat(controller.findServerCohortDiscord("CURRENT_MEMBERS")).isEqualTo(place)
        val set = controller.setServerCohortDiscord("CURRENT_MEMBERS", DiscordPlaceRequest(roleId = "111", channelIds = listOf("900")))
        assertThat(set).isEqualTo(place)
    }
}

package net.blueshell.api.cohort.web

import net.blueshell.api.cohort.domain.CohortDiscord
import net.blueshell.api.cohort.domain.DiscordChoice
import net.blueshell.api.cohort.domain.DiscordPlace
import org.assertj.core.api.Assertions.assertThat
import org.junit.jupiter.api.Test
import org.mockito.kotlin.mock
import org.mockito.kotlin.whenever

class DiscordPlaceControllerTest {
    private val discord = mock<CohortDiscord>()
    private val controller = DiscordPlaceController(discord, "Committees")
    private val place = DiscordPlace(true, "900", "Sitecie", emptyList())

    @Test
    fun `a committee's place is read and set by its cohort, a new channel going under Committees`() {
        whenever(discord.read("COMMITTEE_MEMBERS:7")).thenReturn(place)
        whenever(discord.apply("COMMITTEE_MEMBERS:7", DiscordChoice(createRole = true, createChannel = "sitecie"), "Committees"))
            .thenReturn(place)

        assertThat(controller.findCommitteeDiscord(7)).isEqualTo(place)
        assertThat(controller.setCommitteeDiscord(7, DiscordPlaceRequest(createRole = true, createChannel = "sitecie"))).isEqualTo(place)
        assertThat(DiscordPlaceRequest(roleId = "901").choice()).isEqualTo(DiscordChoice(roleId = "901"))
    }
}

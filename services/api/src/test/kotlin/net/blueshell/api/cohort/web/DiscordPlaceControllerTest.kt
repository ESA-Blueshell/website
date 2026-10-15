package net.blueshell.api.cohort.web

import jakarta.validation.Validation
import net.blueshell.api.cohort.domain.CohortDiscord
import net.blueshell.api.cohort.domain.DiscordChoice
import net.blueshell.api.cohort.domain.DiscordPlace
import org.assertj.core.api.Assertions.assertThat
import org.junit.jupiter.api.Test
import org.mockito.kotlin.mock
import org.mockito.kotlin.verify
import org.mockito.kotlin.whenever

class DiscordPlaceControllerTest {
    private val discord = mock<CohortDiscord>()
    private val controller = DiscordPlaceController(discord, "Committees", "Esports")
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

    @Test
    fun `a team's place is read, set and removed by its cohort, its channel going under Esports`() {
        whenever(discord.read("TEAM_PLAYERS:3")).thenReturn(place)
        whenever(discord.apply("TEAM_PLAYERS:3", DiscordChoice(createRole = true), "Esports")).thenReturn(place)

        assertThat(controller.findTeamDiscord(3)).isEqualTo(place)
        assertThat(controller.setTeamDiscord(3, DiscordPlaceRequest(createRole = true))).isEqualTo(place)
        controller.removeTeamDiscord(3)

        verify(discord).remove("TEAM_PLAYERS:3", "Esports")
    }

    @Test
    fun `a new channel's name fits Discord's limit`() {
        val validator = Validation.buildDefaultValidatorFactory().validator

        assertThat(validator.validate(DiscordPlaceRequest(createChannel = "x".repeat(100)))).isEmpty()
        assertThat(validator.validate(DiscordPlaceRequest(createChannel = "x".repeat(101)))).hasSize(1)
    }
}

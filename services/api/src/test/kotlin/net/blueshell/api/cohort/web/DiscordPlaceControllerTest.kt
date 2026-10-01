package net.blueshell.api.cohort.web

import net.blueshell.api.cohort.domain.AdoptionMatch
import net.blueshell.api.cohort.domain.CohortDiscord
import net.blueshell.api.cohort.domain.DiscordAdoption
import net.blueshell.api.cohort.domain.DiscordChoice
import net.blueshell.api.cohort.domain.DiscordPlace
import net.blueshell.api.cohort.persistence.CohortType
import org.assertj.core.api.Assertions.assertThat
import org.junit.jupiter.api.Test
import org.mockito.kotlin.mock
import org.mockito.kotlin.verify
import org.mockito.kotlin.whenever

class DiscordPlaceControllerTest {
    private val discord: CohortDiscord = mock()
    private val adoption: DiscordAdoption = mock()
    private val controller = DiscordPlaceController(discord, adoption, "Committees", "Esports")
    private val place = DiscordPlace(true, "900", "Sitecie", emptyList())

    @Test
    fun `reads and sets a committee's role and channels, new channels under Committees`() {
        whenever(discord.read("COMMITTEE_MEMBERS:7")).thenReturn(place)
        whenever(discord.apply("COMMITTEE_MEMBERS:7", DiscordChoice(null, true, listOf("1"), "sitecie"), "Committees")).thenReturn(place)

        assertThat(controller.findCommitteeDiscord(7)).isSameAs(place)
        assertThat(
            controller.setCommitteeDiscord(7, DiscordPlaceRequest(createRole = true, channelIds = listOf("1"), createChannel = "sitecie")),
        ).isSameAs(place)
        assertThat(DiscordPlaceRequest().channelIds).isEmpty()
    }

    @Test
    fun `reads, sets and removes a team's role and channel, new channels under Esports`() {
        whenever(discord.read("TEAM_PLAYERS:3")).thenReturn(place)
        whenever(discord.apply("TEAM_PLAYERS:3", DiscordChoice(createRole = true), "Esports")).thenReturn(place)

        assertThat(controller.findTeamDiscord(3)).isSameAs(place)
        assertThat(controller.setTeamDiscord(3, DiscordPlaceRequest(createRole = true))).isSameAs(place)
        controller.removeTeamDiscord(3)
        verify(discord).remove("TEAM_PLAYERS:3")
    }

    @Test
    fun `lists the matches by name and links the confirmed ones`() {
        val matches = listOf(AdoptionMatch("COMMITTEE_MEMBERS:1", "Sitecie", CohortType.COMMITTEE_MEMBERS, "901", "Sitecie", emptyList()))
        whenever(adoption.proposals()).thenReturn(matches)
        whenever(adoption.adopt(listOf("COMMITTEE_MEMBERS:1"))).thenReturn(1)

        assertThat(controller.listDiscordMatches()).isSameAs(matches)
        assertThat(controller.adoptDiscordMatches(AdoptDiscordRequest(listOf("COMMITTEE_MEMBERS:1")))).isEqualTo(AdoptDiscordResponse(1))
    }
}

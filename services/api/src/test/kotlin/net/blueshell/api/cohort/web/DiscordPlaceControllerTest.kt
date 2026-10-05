package net.blueshell.api.cohort.web

import net.blueshell.api.cohort.domain.AdoptionMatch
import net.blueshell.api.cohort.domain.AdoptionOutcome
import net.blueshell.api.cohort.domain.CohortDiscord
import net.blueshell.api.cohort.domain.DiscordAdoption
import net.blueshell.api.cohort.domain.DiscordChoice
import net.blueshell.api.cohort.domain.DiscordPlace
import net.blueshell.api.cohort.domain.RefusedMatch
import net.blueshell.api.cohort.persistence.CohortType
import org.assertj.core.api.Assertions.assertThat
import org.assertj.core.api.Assertions.assertThatThrownBy
import org.junit.jupiter.api.Test
import org.mockito.kotlin.mock
import org.mockito.kotlin.verify
import org.mockito.kotlin.whenever
import org.springframework.web.server.ResponseStatusException

class DiscordPlaceControllerTest {
    private val discord: CohortDiscord = mock()
    private val adoption: DiscordAdoption = mock()
    private val controller = DiscordPlaceController(discord, adoption, "Committees", "Esports", "Board")
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
    fun `reads and sets a board's role and channels by where the board stands, new channels under Board`() {
        whenever(discord.read("BOARD")).thenReturn(place)
        whenever(discord.read("KANDI")).thenReturn(place)
        whenever(discord.apply("BOARD_YEAR_MEMBERS:12", DiscordChoice(roleId = "910", channelIds = listOf("4")), "Board")).thenReturn(place)

        assertThat(controller.findBoardDiscord("BOARD")).isSameAs(place)
        assertThat(controller.findBoardDiscord("KANDI")).isSameAs(place)
        val asked = DiscordPlaceRequest(roleId = "910", channelIds = listOf("4"))
        assertThat(controller.setBoardDiscord("BOARD_YEAR_MEMBERS:12", asked)).isSameAs(place)
        // Only a board's own cohorts are reached from here.
        assertThatThrownBy { controller.findBoardDiscord("COMMITTEE_MEMBERS:7") }
            .isInstanceOf(ResponseStatusException::class.java)
            .hasMessageContaining("404")
    }

    @Test
    fun `unlinks a role from the cohort that follows it`() {
        controller.unlinkDiscordRole("900")

        verify(discord).unlink("900")
    }

    @Test
    fun `lists the matches by name and links the confirmed ones`() {
        val matches = listOf(AdoptionMatch("COMMITTEE_MEMBERS:1", "Sitecie", CohortType.COMMITTEE_MEMBERS, "901", "Sitecie", emptyList()))
        whenever(adoption.proposals()).thenReturn(matches)
        val refused = listOf(RefusedMatch("Lancie", "The bot may not change lancie."))
        whenever(adoption.adopt(listOf("COMMITTEE_MEMBERS:1"))).thenReturn(AdoptionOutcome(1, refused))

        assertThat(controller.listDiscordMatches()).isSameAs(matches)
        val answered = controller.adoptDiscordMatches(AdoptDiscordRequest(listOf("COMMITTEE_MEMBERS:1")))
        assertThat(answered).isEqualTo(AdoptDiscordResponse(1, refused))
        assertThat(answered.refused.single().label).isEqualTo("Lancie")
    }
}

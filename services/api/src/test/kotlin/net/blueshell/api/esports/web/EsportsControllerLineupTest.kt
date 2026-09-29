package net.blueshell.api.esports.web

import net.blueshell.api.esports.api.DraftEntry
import net.blueshell.api.esports.api.LineupDraft
import net.blueshell.api.esports.api.PublishedLineup
import net.blueshell.api.esports.api.RosterEntryInput
import net.blueshell.api.esports.api.TeamRosterService
import net.blueshell.api.esports.persistence.Season
import net.blueshell.api.esports.persistence.Team
import net.blueshell.api.esports.persistence.TeamRosterEntry
import net.blueshell.api.esports.persistence.TeamSeason
import net.blueshell.api.shared.enums.TeamRole
import org.assertj.core.api.Assertions.assertThat
import org.junit.jupiter.api.Test
import org.mockito.kotlin.any
import org.mockito.kotlin.argumentCaptor
import org.mockito.kotlin.mock
import org.mockito.kotlin.verify
import org.mockito.kotlin.whenever
import java.time.LocalDate

class EsportsControllerLineupTest {
    private val rosters = mock<TeamRosterService>()
    private val controller = EsportsController(mock(), mock(), mock(), rosters, mock(), mock())

    @Test
    fun `hands the whole draft to one publish and answers with the team and its roster`() {
        val team = Team(name = "BS Draft").also { it.id = 3 }
        val season =
            Season(name = "Autumn 2030", startDate = LocalDate.of(2030, 9, 1), endDate = LocalDate.of(2031, 1, 31)).also {
                it.id =
                    5
            }
        val entry =
            TeamRosterEntry(
                teamSeason = TeamSeason(team = team, game = "CS2", season = season),
                handle = "first",
            ).also { it.id = 11 }
        whenever(rosters.publish(any())).thenReturn(PublishedLineup(team, listOf(entry)))

        val answer =
            controller.publishLineup(
                5,
                PublishLineupRequest(
                    teamId = 3,
                    name = "BS Draft",
                    game = "CS2",
                    removed = listOf(12),
                    entries = listOf(LineupEntryRequest(id = 11, handle = "first", role = TeamRole.COACH, userId = 42)),
                ),
            )

        val draft = argumentCaptor<LineupDraft>()
        verify(rosters).publish(draft.capture())
        assertThat(draft.firstValue.seasonId).isEqualTo(5)
        assertThat(draft.firstValue.removed).containsExactly(12)
        assertThat(draft.firstValue.entries)
            .containsExactly(DraftEntry(11, RosterEntryInput("first", TeamRole.COACH), 42))
        assertThat(answer.team.id).isEqualTo(3)
        assertThat(answer.roster.map { it.handle }).containsExactly("first")
    }
}

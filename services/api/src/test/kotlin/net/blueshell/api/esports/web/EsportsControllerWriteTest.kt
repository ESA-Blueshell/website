package net.blueshell.api.esports.web

import net.blueshell.api.esports.api.RosterEntryInput
import net.blueshell.api.esports.api.TeamRosterService
import net.blueshell.api.esports.domain.SeasonInput
import net.blueshell.api.esports.domain.SeasonService
import net.blueshell.api.esports.domain.TeamInput
import net.blueshell.api.esports.domain.TeamService
import net.blueshell.api.esports.persistence.Season
import net.blueshell.api.esports.persistence.Team
import net.blueshell.api.esports.persistence.TeamRosterEntry
import net.blueshell.api.esports.persistence.TeamSeason
import net.blueshell.api.shared.enums.TeamRole
import org.assertj.core.api.Assertions.assertThat
import org.junit.jupiter.api.Test
import org.mockito.kotlin.any
import org.mockito.kotlin.anyOrNull
import org.mockito.kotlin.mock
import org.mockito.kotlin.verify
import org.mockito.kotlin.whenever
import java.time.LocalDate

/** Each esports write hands its service one input, mapped from the request in one expression. */
class EsportsControllerWriteTest {
    private val seasons = mock<SeasonService>()
    private val teams = mock<TeamService>()
    private val rosters = mock<TeamRosterService>()
    private val controller = EsportsController(mock(), seasons, teams, mock(), rosters, mock(), mock())

    private val start = LocalDate.of(2030, 9, 1)
    private val end = LocalDate.of(2031, 1, 31)
    private val season = Season(name = "Autumn", startDate = start, endDate = end).also { it.id = 5 }
    private val team = Team(name = "BS Nomads").also { it.id = 3 }
    private val entry =
        TeamRosterEntry(teamSeason = TeamSeason(team = team, game = "CS2", season = season), handle = "nova").also { it.id = 11 }

    @Test
    fun `a season is made and edited from its input`() {
        whenever(seasons.create(any())).thenReturn(season)
        whenever(seasons.update(any(), any())).thenReturn(season)
        val request = SeasonRequest("Autumn", start, end)

        controller.createSeason(request)
        controller.updateSeason(5, request)

        verify(seasons).create(SeasonInput("Autumn", start, end))
        verify(seasons).update(5, SeasonInput("Autumn", start, end))
    }

    @Test
    fun `a team is made and edited from its input`() {
        whenever(teams.create(any())).thenReturn(team)
        whenever(teams.update(any(), any())).thenReturn(team)

        controller.createTeam(TeamRequest("BS Nomads"))
        controller.updateTeam(3, TeamRequest("BS Nomads"))

        verify(teams).create(TeamInput("BS Nomads"))
        verify(teams).update(3, TeamInput("BS Nomads"))
    }

    @Test
    fun `somebody is put on a roster and edited from one input`() {
        whenever(rosters.add(any(), any(), any(), any(), anyOrNull())).thenReturn(entry)
        whenever(rosters.update(any(), any(), any())).thenReturn(entry)

        controller.addRosterEntry(3, AddRosterEntryRequest("CS2", 5, "nova", TeamRole.PLAYER, 9))
        val answer = controller.updateRosterEntry(11, UpdateRosterEntryRequest("nova", TeamRole.PLAYER, sortIndex = 2))

        verify(rosters).add(3, "CS2", 5, RosterEntryInput("nova", TeamRole.PLAYER), 9)
        verify(rosters).update(11, RosterEntryInput("nova", TeamRole.PLAYER), 2)
        assertThat(answer.id).isEqualTo(11)
    }
}

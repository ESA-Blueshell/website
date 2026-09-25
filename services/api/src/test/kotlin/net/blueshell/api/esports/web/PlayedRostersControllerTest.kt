package net.blueshell.api.esports.web

import net.blueshell.api.esports.api.TeamRosterService
import net.blueshell.api.esports.persistence.Season
import net.blueshell.api.esports.persistence.Team
import net.blueshell.api.esports.persistence.TeamRosterEntry
import net.blueshell.api.esports.persistence.TeamRosterEntryRepository
import net.blueshell.api.esports.persistence.TeamSeason
import net.blueshell.api.shared.enums.TeamRole
import org.assertj.core.api.Assertions.assertThat
import org.junit.jupiter.api.Test
import org.mockito.kotlin.mock
import org.mockito.kotlin.whenever
import java.time.LocalDate

class PlayedRostersControllerTest {
    private val entries = mock<TeamRosterEntryRepository>()
    private val rosters = TeamRosterService(entries, mock(), mock(), mock(), mock(), mock())
    private val controller = PlayedRostersController(rosters)

    @Test
    fun `answers every roster spot a person held, with its game, season, team and role`() {
        val season = Season(name = "Spring", startDate = LocalDate.of(2026, 2, 1), endDate = LocalDate.of(2026, 6, 1)).also { it.id = 1 }
        val team = Team(name = "Blue Shells").also { it.id = 3 }
        val spot =
            TeamRosterEntry(
                teamSeason = TeamSeason(team = team, game = "VALORANT", season = season),
                handle = "alice",
                teamRole = TeamRole.COACH,
                roleTitle = "Head coach",
                userId = 7,
            )
        whenever(entries.findAllByUserId(7)).thenReturn(listOf(spot))

        assertThat(controller.findPlayedRosters(7)).containsExactly(
            PlayedRosterResponse(
                game = "VALORANT",
                seasonId = 1,
                seasonName = "Spring",
                seasonStart = LocalDate.of(2026, 2, 1),
                teamId = 3,
                teamName = "Blue Shells",
                role = TeamRole.COACH,
                roleTitle = "Head coach",
            ),
        )
    }
}

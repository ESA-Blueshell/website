package net.blueshell.api.esports.domain

import net.blueshell.api.esports.persistence.Season
import net.blueshell.api.esports.persistence.Team
import net.blueshell.api.esports.persistence.TeamSeason
import net.blueshell.api.esports.persistence.TeamSeasonRepository
import org.assertj.core.api.Assertions.assertThat
import org.junit.jupiter.api.Test
import org.mockito.kotlin.mock
import org.mockito.kotlin.whenever
import java.time.LocalDate

class TeamSeasonServiceReadTest {
    private val fielded = mock<TeamSeasonRepository>()
    private val service = TeamSeasonService(fielded, mock(), mock(), mock(), mock(), mock())

    @Test
    fun `reads every team's fieldings in one query`() {
        val season = Season(name = "Autumn 2030", startDate = LocalDate.of(2030, 9, 1), endDate = LocalDate.of(2031, 1, 31))
        val fielding = TeamSeason(team = Team(name = "BS Draft"), game = "CS2", season = season)
        whenever(fielded.findAllWithSeason()).thenReturn(listOf(fielding))

        assertThat(service.everyFielding()).containsExactly(fielding)
    }
}

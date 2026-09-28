package net.blueshell.api.esports.domain

import net.blueshell.api.esports.persistence.Team
import net.blueshell.api.esports.persistence.TeamRepository
import org.assertj.core.api.Assertions.assertThat
import org.assertj.core.api.Assertions.assertThatThrownBy
import org.junit.jupiter.api.Test
import org.mockito.kotlin.mock
import org.mockito.kotlin.whenever
import java.util.Optional

class TeamServiceReadTest {
    private val teams = mock<TeamRepository>()
    private val service = TeamService(teams, mock(), mock())

    @Test
    fun `reads a team, and refuses one that is not there`() {
        val team = Team(name = "BS Nomads")
        whenever(teams.findById(1)).thenReturn(Optional.of(team))
        whenever(teams.findById(2)).thenReturn(Optional.empty())

        assertThat(service.findById(1)).isSameAs(team)
        assertThatThrownBy { service.findById(2) }.isInstanceOf(TeamNotFoundException::class.java)
    }
}

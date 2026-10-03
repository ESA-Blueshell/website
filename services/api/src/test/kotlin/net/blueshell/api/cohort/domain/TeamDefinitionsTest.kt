package net.blueshell.api.cohort.domain

import net.blueshell.api.cohort.persistence.CohortType
import net.blueshell.api.esports.api.TeamRosterService
import org.assertj.core.api.Assertions.assertThat
import org.junit.jupiter.api.Test
import org.mockito.kotlin.mock
import org.mockito.kotlin.whenever

class TeamDefinitionsTest {
    @Test
    fun `every team is a cohort of the players on its line-up in the season fielded now`() {
        val rosters: TeamRosterService = mock()
        whenever(rosters.teamNames()).thenReturn(mapOf(3L to "BS Draft"))
        whenever(rosters.currentPlayersOf(3)).thenReturn(setOf(7L))

        val definition = TeamPlayersProvider(rosters).definitions().single()

        assertThat(listOf(definition.key, definition.type, definition.scope, definition.label, definition.folder))
            .containsExactly("TEAM_PLAYERS:3", CohortType.TEAM_PLAYERS, 3L, "BS Draft", "Teams")
        assertThat(definition.members()).containsExactly(7L)
        assertThat(definition.contains(7)).isTrue()
        assertThat(definition.contains(8)).isFalse()
        assertThat(CohortType.TEAM_PLAYERS.listedOnBrevo).isFalse()
        assertThat(TeamPlayersProvider(rosters).type).isEqualTo(CohortType.TEAM_PLAYERS)
    }
}

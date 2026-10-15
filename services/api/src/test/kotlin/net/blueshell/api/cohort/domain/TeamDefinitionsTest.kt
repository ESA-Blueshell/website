package net.blueshell.api.cohort.domain

import net.blueshell.api.cohort.persistence.CohortCategory
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
        whenever(rosters.teamNames()).thenReturn(mapOf(3L to "BS Draft", 4L to "BS Old"))
        whenever(rosters.currentLineups()).thenReturn(mapOf(3L to setOf(7L)))

        val (draft, old) = TeamPlayersProvider(rosters).definitions()

        assertThat(listOf(draft.key, draft.type, draft.scope, draft.label, draft.folder))
            .containsExactly("TEAM_PLAYERS:3", CohortType.TEAM_PLAYERS, 3L, "BS Draft", "Teams")
        assertThat(draft.members()).containsExactly(7L)
        assertThat(draft.contains(7)).isTrue()
        assertThat(draft.contains(8)).isFalse()
        // A team not fielded now holds nobody.
        assertThat(old.members()).isEmpty()
        assertThat(CohortType.TEAM_PLAYERS.listedOnBrevo).isFalse()
        assertThat(CohortType.TEAM_PLAYERS.category()).isEqualTo(CohortCategory.TEAMS)
        assertThat(TeamPlayersProvider(rosters).type).isEqualTo(CohortType.TEAM_PLAYERS)
    }
}

package net.blueshell.api.esports.domain

import net.blueshell.api.esports.api.TeamRosterService
import net.blueshell.api.esports.persistence.Season
import net.blueshell.api.esports.persistence.Team
import net.blueshell.api.esports.persistence.TeamRosterEntry
import net.blueshell.api.esports.persistence.TeamSeason
import net.blueshell.api.game.api.GameService
import net.blueshell.api.user.api.UserService
import net.blueshell.api.user.persistence.User
import org.assertj.core.api.Assertions.assertThat
import org.junit.jupiter.api.Test
import org.mockito.kotlin.mock
import org.mockito.kotlin.whenever
import java.time.LocalDate

class EsportsQueryServiceTest {
    private val rosters = mock<TeamRosterService>()
    private val fielded = mock<TeamSeasonService>()
    private val accounts = mock<UserGameAccountService>()
    private val users = mock<UserService>()
    private val games = mock<GameService>()
    private val query = EsportsQueryService(rosters, mock(), fielded, accounts, users, games, mock())

    private fun person(
        id: Long,
        named: Boolean,
    ) = User(
        username = "u$id",
        email = "u$id@example.com",
        password = "h",
        initials = "U",
        firstName = "Una",
        lastName = "$id",
    ).also {
        it.id = id
        it.nameOnRosters = named
    }

    @Test
    fun `a player is named only where they said so, member or not`() {
        val season = Season(name = "Spring", startDate = LocalDate.of(2026, 2, 1), endDate = LocalDate.of(2026, 6, 1)).also { it.id = 1 }
        val team = Team(name = "Blue Shells").also { it.id = 3 }
        val squad = TeamSeason(team = team, game = "VALORANT", season = season)
        whenever(games.codes()).thenReturn(listOf("VALORANT"))
        whenever(fielded.findByGameAndSeason("VALORANT", 1)).thenReturn(listOf(squad))
        whenever(rosters.findByGameAndSeason("VALORANT", 1)).thenReturn(
            listOf(
                TeamRosterEntry(teamSeason = squad, handle = "said-yes", userId = 7),
                TeamRosterEntry(teamSeason = squad, handle = "said-no", userId = 8),
            ),
        )
        whenever(accounts.handlesFor("VALORANT", setOf(7L, 8L))).thenReturn(emptyMap())
        whenever(users.findAllByIds(setOf(7L, 8L))).thenReturn(listOf(person(7, named = true), person(8, named = false)))

        val members = query.gamesOf(1, mayEdit = false).single().teams.single().members

        assertThat(members.map { it.handle to it.name }).containsExactly("said-yes" to "Una 7", "said-no" to null)
    }
}

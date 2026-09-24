package net.blueshell.api.esports.domain

import net.blueshell.api.esports.persistence.TeamRosterEntryRepository
import net.blueshell.api.esports.persistence.TeamSeasonRepository
import net.blueshell.api.game.api.GameService
import net.blueshell.api.game.persistence.Game
import org.assertj.core.api.Assertions.assertThat
import org.assertj.core.api.Assertions.assertThatThrownBy
import org.junit.jupiter.api.Test
import org.mockito.kotlin.mock
import org.mockito.kotlin.whenever

class FieldedGamesTest {
    private val games = mock<GameService>()
    private val fielded = mock<TeamSeasonRepository>()
    private val entries = mock<TeamRosterEntryRepository>()
    private val seasons = mock<TeamSeasonService>()
    private val holdings = FieldedGames(games, fielded, entries, seasons)

    init {
        whenever(games.requireGame("VALORANT")).thenReturn(Game(code = "VALORANT", name = "Valorant", slug = "valorant"))
    }

    @Test
    fun `counts the teams fielded in a game and the people on them`() {
        whenever(fielded.countTeamsByGame("VALORANT")).thenReturn(3L)
        whenever(entries.countByGame("VALORANT")).thenReturn(14L)

        assertThat(holdings.contentsOf("VALORANT")).isEqualTo(3L to 14L)
    }

    @Test
    fun `refuses to let a game with teams go, and lets one without any go`() {
        whenever(fielded.countTeamsByGame("VALORANT")).thenReturn(3L, 0L)
        whenever(entries.countByGame("VALORANT")).thenReturn(14L, 0L)

        assertThatThrownBy { holdings.refuseRemoval("VALORANT") }.isInstanceOf(GameHoldsHistory::class.java)
        holdings.refuseRemoval("VALORANT")
    }

    @Test
    fun `says what a removal would take from competition`() {
        whenever(fielded.countTeamsByGame("VALORANT")).thenReturn(3L)
        whenever(entries.countByGame("VALORANT")).thenReturn(14L)

        assertThat(holdings.heldAgainst("VALORANT")).isEqualTo(mapOf("teams" to 3L, "players" to 14L))
    }

    @Test
    fun `says which games are fielded this season`() {
        whenever(seasons.currentlyPlayed()).thenReturn(setOf("VALORANT"))

        assertThat(holdings.currentlyFielded()).containsExactly("VALORANT")
    }
}

package net.blueshell.api.esports.web

import net.blueshell.api.esports.api.TeamRosterService
import net.blueshell.api.esports.domain.EsportsQueryService
import net.blueshell.api.esports.domain.FieldedGames
import net.blueshell.api.esports.domain.SeasonGameService
import net.blueshell.api.esports.domain.SeasonService
import net.blueshell.api.esports.domain.TeamSeasonService
import net.blueshell.api.esports.domain.TeamService
import net.blueshell.api.game.api.GameService
import org.junit.jupiter.api.Test
import org.mockito.kotlin.mock
import org.mockito.kotlin.verify

class EsportsControllerTest {
    private val games = mock<GameService>()
    private val controller =
        EsportsController(
            mock<EsportsQueryService>(),
            mock<SeasonService>(),
            mock<TeamService>(),
            games,
            mock<TeamRosterService>(),
            mock<TeamSeasonService>(),
            mock<SeasonGameService>(),
            mock<FieldedGames>(),
        )

    @Test
    fun `removing a game from the competition pages archives and removes it in one step, keeping its row`() {
        controller.deleteGame("CSGO")

        verify(games).remove("CSGO", archiveFirst = true)
    }
}

package net.blueshell.api.game.web

import net.blueshell.api.game.api.GameCompetition
import net.blueshell.api.game.api.GameService
import net.blueshell.api.game.persistence.Game
import net.blueshell.api.game.persistence.GameChannel
import org.assertj.core.api.Assertions.assertThat
import org.junit.jupiter.api.Test
import org.mockito.kotlin.mock
import org.mockito.kotlin.verify
import org.mockito.kotlin.whenever

class GameControllerTest {
    private val games = mock<GameService>()
    private val controller = GameController(games)

    @Test
    fun `lists every game with whether it is archived and whether it is in competition`() {
        val valorant = Game(code = "VALORANT", name = "Valorant", slug = "valorant", accent = "#ff4655", intro = "Stacks", sortIndex = 1)
        val dota = Game(code = "DOTA_2", name = "Dota 2", slug = "dota-2", sortIndex = 2, archived = true)
        whenever(games.findAll()).thenReturn(listOf(valorant, dota))
        whenever(games.inCompetition()).thenReturn(setOf("VALORANT"))

        val listed = controller.findCasualGames()

        assertThat(listed.map { it.code }).containsExactly("VALORANT", "DOTA_2")
        assertThat(listed[0]).isEqualTo(
            CasualGameResponse(
                code = "VALORANT",
                name = "Valorant",
                slug = "valorant",
                accent = "#ff4655",
                intro = "Stacks",
                banner = null,
                icon = null,
                sortIndex = 1,
                archived = false,
                inCompetition = true,
            ),
        )
        assertThat(listed[1].archived).isTrue()
        assertThat(listed[1].inCompetition).isFalse()
    }

    private val chess = Game(code = "CHESS", name = "Chess", slug = "chess", sortIndex = 4)

    @Test
    fun `adds and corrects a game, moving it only where the request says where it sits`() {
        val request =
            CasualGameRequest(
                name = "Chess",
                slug = "chess",
                intro = "Blitz",
                accent = "#b58863",
                banner = "b.webp",
                icon = "i.webp",
                channels = listOf(GameChannelRequest("13", "324", "chess")),
                competitionIntro = "Rated",
                esportsChannels = listOf(GameChannelRequest("14", "324", "chess-esports")),
            )
        val asked = listOf(GameChannel("13", "324", "chess"))
        val esports = listOf(GameChannel("14", "324", "chess-esports"))
        chess.channels.addAll(asked)
        chess.esportsChannels.addAll(esports)
        chess.competitionIntro = "Rated"
        val competition = GameCompetition("Rated", esports)
        whenever(games.create("Chess", "chess", "Blitz", "#b58863", "b.webp", "i.webp", null, asked, competition)).thenReturn(chess)
        whenever(games.update("CHESS", "Chess", "chess", "Blitz", "#b58863", "b.webp", "i.webp", 2, null, GameCompetition("Rated", null)))
            .thenReturn(chess)
        whenever(games.inCompetition()).thenReturn(emptySet())

        val added = controller.createCasualGame(request)
        assertThat(added.channels).containsExactly(GameChannelResponse("13", "324", "chess"))
        assertThat(added.esportsChannels).containsExactly(GameChannelResponse("14", "324", "chess-esports"))
        assertThat(added.competitionIntro).isEqualTo("Rated")
        assertThat(controller.updateCasualGame("CHESS", request.copy(channels = null, esportsChannels = null, sortIndex = 2)).inCompetition)
            .isFalse()
    }

    @Test
    fun `archives a game and removes it, answering what the removal would touch first`() {
        whenever(games.archive("CHESS", true)).thenReturn(chess.apply { archived = true })
        whenever(games.inCompetition()).thenReturn(emptySet())
        whenever(games.heldAgainst("CHESS")).thenReturn(mapOf("events" to 3L, "committees" to 1L))

        assertThat(controller.archiveGame("CHESS", ArchiveGameRequest(archived = true)).archived).isTrue()
        assertThat(controller.findGameHoldings("CHESS"))
            .isEqualTo(GameHoldingsResponse(channels = 0, committees = 1, events = 3, teams = 0, players = 0))
        controller.removeGame("CHESS")
        verify(games).remove("CHESS")
    }
}

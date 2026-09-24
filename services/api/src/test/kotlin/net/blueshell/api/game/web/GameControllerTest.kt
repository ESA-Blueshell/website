package net.blueshell.api.game.web

import net.blueshell.api.game.api.GameService
import net.blueshell.api.game.persistence.Game
import org.assertj.core.api.Assertions.assertThat
import org.junit.jupiter.api.Test
import org.mockito.kotlin.mock
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
}

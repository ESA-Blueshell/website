package net.blueshell.api.game.web

import net.blueshell.api.game.persistence.Game
import net.blueshell.api.shared.enums.FileType
import net.blueshell.api.shared.enums.Role
import net.blueshell.api.testsupport.UserTestSupport
import net.blueshell.api.testsupport.countStatements
import org.assertj.core.api.Assertions.assertThat
import org.junit.jupiter.api.Test
import org.springframework.boot.test.context.SpringBootTest
import org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get
import org.springframework.test.web.servlet.result.MockMvcResultMatchers.status

/**
 * Every page reads `/games`, so its statement count must not grow with the games: each one's
 * banner and icon are batched rather than read a game at a time.
 */
@SpringBootTest
class GameListFetchIT : UserTestSupport() {
    @Test
    fun `reading the games does not scale queries with the number of games`() {
        val board = createUserWithRole(Role.BOARD)
        val pictured = {
            val code = "FETCH${System.nanoTime()}"
            persist(
                Game(code = code, name = code, slug = code.lowercase()).apply {
                    banner = createFileFixture(board, type = FileType.GAME_BANNER)
                    icon = createFileFixture(board, type = FileType.GAME_ICON)
                },
            )
        }
        pictured()
        // Primed, so one-time statements for the session do not skew the first count.
        readGames()

        val before = countStatements { readGames() }
        repeat(4) { pictured() }
        val after = countStatements { readGames() }

        assertThat(after)
            .describedAs("query count must not grow with the number of games (N+1)")
            .isEqualTo(before)
    }

    private fun readGames() {
        mvc.perform(get("/games")).andExpect(status().isOk)
    }
}

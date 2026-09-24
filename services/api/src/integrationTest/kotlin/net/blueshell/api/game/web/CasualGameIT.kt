package net.blueshell.api.game.web

import net.blueshell.api.game.persistence.GameRepository
import net.blueshell.api.shared.enums.Role
import net.blueshell.api.testsupport.UserTestSupport
import net.blueshell.api.user.persistence.User
import org.assertj.core.api.Assertions.assertThat
import org.junit.jupiter.api.Test
import org.springframework.beans.factory.annotation.Autowired
import org.springframework.boot.test.context.SpringBootTest
import org.springframework.http.MediaType
import org.springframework.jdbc.core.JdbcTemplate
import org.springframework.test.web.servlet.request.MockMvcRequestBuilders.delete
import org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get
import org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post
import org.springframework.test.web.servlet.request.MockMvcRequestBuilders.put
import org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath
import org.springframework.test.web.servlet.result.MockMvcResultMatchers.status

/**
 * The games as the casual pages add, archive and remove them. Removal is soft, so the SQL that
 * stamps a row and the restriction that hides it are what this proves against a real database.
 */
@SpringBootTest
class CasualGameIT : UserTestSupport() {
    @Autowired private lateinit var games: GameRepository

    @Autowired private lateinit var jdbc: JdbcTemplate

    private fun add(
        board: User,
        name: String,
    ) = mvc
        .perform(
            post("/games")
                .with(bearer(board))
                .contentType(MediaType.APPLICATION_JSON)
                .content("""{"name":"$name","slug":"${name.lowercase()}","intro":"Blitz"}"""),
        )

    private fun archive(
        board: User,
        code: String,
        archived: Boolean = true,
    ) = mvc.perform(
        put("/games/{game}/archived", code)
            .with(bearer(board))
            .contentType(MediaType.APPLICATION_JSON)
            .content("""{"archived":$archived}"""),
    )

    @Test
    fun `the board adds a game from the casual pages, and anybody reads it with its archived state`() {
        val board = createUserWithRole(Role.BOARD)
        val name = "Chess${System.nanoTime()}"

        add(board, name)
            .andExpect(status().isCreated)
            .andExpect(jsonPath("$.code").value(name.uppercase()))
            .andExpect(jsonPath("$.archived").value(false))
            .andExpect(jsonPath("$.inCompetition").value(false))

        mvc
            .perform(get("/games"))
            .andExpect(status().isOk)
            .andExpect(jsonPath("$[?(@.code == '${name.uppercase()}')].intro").value("Blitz"))
    }

    @Test
    fun `a member cannot add, archive or remove a game`() {
        val member = createUserWithRole(Role.MEMBER)

        add(member, "Pong${System.nanoTime()}").andExpect(status().isForbidden)
        archive(member, "VALORANT").andExpect(status().isForbidden)
        mvc.perform(delete("/games/{game}", "VALORANT").with(bearer(member))).andExpect(status().isForbidden)
    }

    @Test
    fun `the board corrects a game and archives it, and it keeps its page`() {
        val board = createUserWithRole(Role.BOARD)
        val name = "Wordle${System.nanoTime()}"
        add(board, name).andExpect(status().isCreated)

        mvc
            .perform(
                put("/games/{game}", name.uppercase())
                    .with(bearer(board))
                    .contentType(MediaType.APPLICATION_JSON)
                    .content("""{"name":"$name","slug":"${name.lowercase()}-daily","intro":"One word a day"}"""),
            ).andExpect(status().isOk)
            .andExpect(jsonPath("$.slug").value("${name.lowercase()}-daily"))
        archive(board, name.uppercase()).andExpect(jsonPath("$.archived").value(true))

        mvc
            .perform(get("/games"))
            .andExpect(jsonPath("$[?(@.code == '${name.uppercase()}')].archived").value(true))
    }

    @Test
    fun `a game still played is refused removal, and an archived one is removed with its row kept`() {
        val board = createUserWithRole(Role.BOARD)
        val name = "Go${System.nanoTime()}"
        val code = name.uppercase()
        add(board, name).andExpect(status().isCreated)

        mvc
            .perform(delete("/games/{game}", code).with(bearer(board)))
            .andExpect(status().isConflict)
            .andExpect(jsonPath("$.code").value("GameNotArchived"))
        archive(board, code)
        mvc
            .perform(get("/games/{game}/holdings", code).with(bearer(board)))
            .andExpect(status().isOk)
            .andExpect(jsonPath("$.teams").value(0))
        mvc.perform(delete("/games/{game}", code).with(bearer(board))).andExpect(status().isNoContent)

        mvc.perform(get("/games")).andExpect(jsonPath("$[?(@.code == '$code')]").isEmpty)
        assertThat(games.findByCode(code)).isNull()
        assertThat(jdbc.queryForObject("SELECT COUNT(*) FROM game WHERE code = ? AND deleted_at < '9999-12-31'", Int::class.java, code))
            .isEqualTo(1)
    }

    @Test
    fun `adding a removed game again brings it back, played rather than archived`() {
        val board = createUserWithRole(Role.BOARD)
        val name = "Risk${System.nanoTime()}"
        val code = name.uppercase()
        add(board, name).andExpect(status().isCreated)
        archive(board, code)
        mvc.perform(delete("/games/{game}", code).with(bearer(board))).andExpect(status().isNoContent)

        add(board, name)
            .andExpect(status().isCreated)
            .andExpect(jsonPath("$.code").value(code))
            .andExpect(jsonPath("$.archived").value(false))

        assertThat(jdbc.queryForObject("SELECT COUNT(*) FROM game WHERE code = ?", Int::class.java, code)).isEqualTo(1)
    }
}

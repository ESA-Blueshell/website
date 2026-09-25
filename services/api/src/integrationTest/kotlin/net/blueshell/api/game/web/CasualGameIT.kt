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
                .with(signedIn(board))
                .contentType(MediaType.APPLICATION_JSON)
                .content("""{"name":"$name","slug":"${name.lowercase()}","intro":"Blitz"}"""),
        )

    private fun archive(
        board: User,
        code: String,
        archived: Boolean = true,
    ) = mvc.perform(
        put("/games/{game}/archived", code)
            .with(signedIn(board))
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
    fun `a highlight colour is a hash and six hex digits, or nothing`() {
        val board = createUserWithRole(Role.BOARD)
        val name = "Go${System.nanoTime()}"
        val game = { accent: String ->
            post("/games")
                .with(signedIn(board))
                .contentType(MediaType.APPLICATION_JSON)
                .content("""{"name":"$name","slug":"${name.lowercase()}","accent":"$accent"}""")
        }

        mvc.perform(game("blue")).andExpect(status().isBadRequest)
        mvc.perform(game("#12345")).andExpect(status().isBadRequest)
        mvc.perform(game("#1F6feb")).andExpect(status().isCreated).andExpect(jsonPath("$.accent").value("#1F6feb"))
    }

    @Test
    fun `a member cannot add, archive or remove a game`() {
        val member = createUserWithRole(Role.MEMBER)

        add(member, "Pong${System.nanoTime()}").andExpect(status().isForbidden)
        archive(member, "VALORANT").andExpect(status().isForbidden)
        mvc.perform(delete("/games/{game}", "VALORANT").with(signedIn(member))).andExpect(status().isForbidden)
    }

    @Test
    fun `the board corrects a game and archives it, and it keeps its page`() {
        val board = createUserWithRole(Role.BOARD)
        val name = "Wordle${System.nanoTime()}"
        add(board, name).andExpect(status().isCreated)

        mvc
            .perform(
                put("/games/{game}", name.uppercase())
                    .with(signedIn(board))
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
            .perform(delete("/games/{game}", code).with(signedIn(board)))
            .andExpect(status().isConflict)
            .andExpect(jsonPath("$.code").value("GameNotArchived"))
        archive(board, code)
        mvc
            .perform(get("/games/{game}/holdings", code).with(signedIn(board)))
            .andExpect(status().isOk)
            .andExpect(jsonPath("$.teams").value(0))
        mvc.perform(delete("/games/{game}", code).with(signedIn(board))).andExpect(status().isNoContent)

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
        mvc.perform(delete("/games/{game}", code).with(signedIn(board))).andExpect(status().isNoContent)

        add(board, name)
            .andExpect(status().isCreated)
            .andExpect(jsonPath("$.code").value(code))
            .andExpect(jsonPath("$.archived").value(false))

        assertThat(jdbc.queryForObject("SELECT COUNT(*) FROM game WHERE code = ?", Int::class.java, code)).isEqualTo(1)
    }

    @Test
    fun `a game keeps the channels chosen for it, several games may share one, and leaving them out keeps them`() {
        val board = createUserWithRole(Role.BOARD)
        val smash = "Smash${System.nanoTime()}"
        val tekken = "Tekken${System.nanoTime()}"
        val channel = """{"id":"900","guildId":"324","name":"fighting-games"}"""
        listOf(smash, tekken).forEach { name ->
            mvc
                .perform(
                    post("/games")
                        .with(signedIn(board))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""{"name":"$name","slug":"${name.lowercase()}","channels":[$channel]}"""),
                ).andExpect(status().isCreated)
                .andExpect(jsonPath("$.channels[0].name").value("fighting-games"))
        }

        mvc
            .perform(
                put("/games/{game}", smash.uppercase())
                    .with(signedIn(board))
                    .contentType(MediaType.APPLICATION_JSON)
                    .content("""{"name":"$smash","slug":"${smash.lowercase()}"}"""),
            ).andExpect(jsonPath("$.channels[0].id").value("900"))
        mvc
            .perform(get("/games/{game}/holdings", tekken.uppercase()).with(signedIn(board)))
            .andExpect(jsonPath("$.channels").value(1))
    }

    @Test
    fun `a game keeps its competition intro and esports channels apart from the casual ones, and both read them`() {
        val board = createUserWithRole(Role.BOARD)
        val name = "Tetris${System.nanoTime()}"
        val casual = """{"id":"901","guildId":"324","name":"tetris"}"""
        val esports = """{"id":"902","guildId":"324","name":"tetris-esports"}"""

        mvc
            .perform(
                post("/games")
                    .with(signedIn(board))
                    .contentType(MediaType.APPLICATION_JSON)
                    .content(
                        """{"name":"$name","slug":"${name.lowercase()}","intro":"Lines","competitionIntro":"Ranked",""" +
                            """"channels":[$casual],"esportsChannels":[$esports]}""",
                    ),
            ).andExpect(status().isCreated)
            .andExpect(jsonPath("$.competitionIntro").value("Ranked"))
            .andExpect(jsonPath("$.channels[0].name").value("tetris"))
            .andExpect(jsonPath("$.esportsChannels[0].name").value("tetris-esports"))

        val code = name.uppercase()
        mvc
            .perform(get("/esports/games"))
            .andExpect(jsonPath("$[?(@.code == '$code')].intro").value("Lines"))
            .andExpect(jsonPath("$[?(@.code == '$code')].competitionIntro").value("Ranked"))
            .andExpect(jsonPath("$[?(@.code == '$code')].esportsChannels[0].id").value("902"))

        mvc
            .perform(
                put("/games/{game}", code)
                    .with(signedIn(board))
                    .contentType(MediaType.APPLICATION_JSON)
                    .content("""{"name":"$name","slug":"${name.lowercase()}","competitionIntro":" "}"""),
            ).andExpect(jsonPath("$.competitionIntro").doesNotExist())
            .andExpect(jsonPath("$.esportsChannels[0].id").value("902"))
    }
}

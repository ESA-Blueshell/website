package net.blueshell.api.event.web

import com.jayway.jsonpath.JsonPath
import net.blueshell.api.shared.enums.Role
import net.blueshell.api.testsupport.UserTestSupport
import net.blueshell.api.user.persistence.User
import org.hamcrest.Matchers.contains
import org.junit.jupiter.api.Test
import org.springframework.boot.test.context.SpringBootTest
import org.springframework.http.MediaType
import org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get
import org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post
import org.springframework.test.web.servlet.request.MockMvcRequestBuilders.put
import org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath
import org.springframework.test.web.servlet.result.MockMvcResultMatchers.status

/** The games an event names, kept in their own table and asked of by a game's page. */
@SpringBootTest
class EventGamesIT : UserTestSupport() {
    private fun addGame(
        board: User,
        name: String,
    ): String {
        val made =
            mvc
                .perform(
                    post("/games")
                        .with(bearer(board))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""{"name":"$name","slug":"${name.lowercase()}"}"""),
                ).andExpect(status().isCreated)
                .andReturn()
        return JsonPath.read(made.response.contentAsString, "$.code")
    }

    private fun archive(
        board: User,
        code: String,
    ) = mvc.perform(
        put("/games/{game}/archived", code)
            .with(bearer(board))
            .contentType(MediaType.APPLICATION_JSON)
            .content("""{"archived":true}"""),
    )

    private fun eventBody(
        committeeId: Long,
        games: List<String>,
    ) = """
        {"committeeId": $committeeId, "title": "Chess night", "description": "Bring a board.",
         "startTime": "2026-10-10T18:00:00Z", "endTime": "2026-10-10T21:00:00Z", "approved": true,
         "membersOnly": false, "signUp": false, "gameCodes": [${games.joinToString { "\"$it\"" }}]}
        """.trimIndent()

    @Test
    fun `an event names games, a game lists its events, and its removal counts them`() {
        val board = createUserWithRole(Role.BOARD)
        val committee = createCommitteeFixture()
        val chess = addGame(board, "Chess${System.nanoTime()}")
        val go = addGame(board, "Go${System.nanoTime()}")

        val created =
            mvc
                .perform(
                    post("/events").with(bearer(board)).contentType(MediaType.APPLICATION_JSON).content(eventBody(committee.id!!, listOf(go, chess))),
                ).andExpect(status().isCreated)
                .andExpect(jsonPath("$.gameCodes", contains(chess, go)))
                .andReturn()
        val id = JsonPath.read<Int>(created.response.contentAsString, "$.id")

        mvc
            .perform(get("/events").param("gameCode", chess))
            .andExpect(status().isOk)
            .andExpect(jsonPath("$.content[*].id", contains(id)))
        mvc
            .perform(get("/games/{game}/holdings", chess).with(bearer(board)))
            .andExpect(jsonPath("$.events").value(1))
    }

    @Test
    fun `an archived game stays named on an event, but cannot be newly picked`() {
        val board = createUserWithRole(Role.BOARD)
        val committee = createCommitteeFixture()
        val dota = addGame(board, "Dota${System.nanoTime()}")
        val created =
            mvc
                .perform(post("/events").with(bearer(board)).contentType(MediaType.APPLICATION_JSON).content(eventBody(committee.id!!, listOf(dota))))
                .andExpect(status().isCreated)
                .andReturn()
        val id = JsonPath.read<Int>(created.response.contentAsString, "$.id")
        archive(board, dota).andExpect(status().isOk)

        mvc
            .perform(post("/events").with(bearer(board)).contentType(MediaType.APPLICATION_JSON).content(eventBody(committee.id!!, listOf(dota))))
            .andExpect(status().isConflict)
            .andExpect(jsonPath("$.code").value("GameArchived"))
        val kept = eventBody(committee.id!!, listOf(dota)).replace("\"gameCodes\"", "\"version\": 0, \"gameCodes\"")
        mvc
            .perform(put("/events/{id}", id).with(bearer(board)).contentType(MediaType.APPLICATION_JSON).content(kept))
            .andExpect(status().isOk)
            .andExpect(jsonPath("$.gameCodes", contains(dota)))
    }
}

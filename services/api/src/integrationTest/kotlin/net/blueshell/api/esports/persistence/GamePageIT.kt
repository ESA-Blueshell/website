package net.blueshell.api.esports.persistence

import net.blueshell.api.shared.enums.Role
import net.blueshell.api.testsupport.UserTestSupport
import org.assertj.core.api.Assertions.assertThatThrownBy
import org.junit.jupiter.api.Test
import org.springframework.boot.test.context.SpringBootTest
import org.springframework.http.MediaType
import org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get
import org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post
import org.springframework.test.web.servlet.request.MockMvcRequestBuilders.put
import org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath
import org.springframework.test.web.servlet.result.MockMvcResultMatchers.status

/**
 * A game's own record: what it is called, the art it is drawn with, the address it answers to,
 * what is said about it, where it sits, and whether the association still fields a team in it.
 * All of it lived in the frontend, where a change to any of it was a deploy.
 */
@SpringBootTest
class GamePageIT : UserTestSupport() {

    /**
     * Nothing is seeded here. The games are what the migration established, and the suite's
     * clean-up restores them after every case rather than wiping them, because a team and a
     * game account point at one.
     */
    @Test
    fun `every game has a page, in the order they are shown`() {
        // Eight games: the six still fielded and the two whose teams are history.
        mvc.perform(get("/esports/games"))
            .andExpect(status().isOk)
            .andExpect(jsonPath("$.length()").value(8))
            .andExpect(jsonPath("$[0].game").value("VALORANT"))
    }

    @Test
    fun `a code naming no game is refused with a reason`() {
        // Nothing makes such a code unrepresentable any more, so the edge has to say so.
        mvc.perform(get("/esports/games/{game}", "PONG"))
            .andExpect(status().isBadRequest)
    }

    @Test
    fun `a team cannot be written for a game that does not exist`() {
        val board = createUserWithRole(Role.BOARD)

        mvc.perform(
            post("/esports/teams")
                .with(bearer(board))
                .contentType(MediaType.APPLICATION_JSON)
                .content("""{"game":"PONG","name":"Table Tennis Firsts"}"""),
        )
            .andExpect(status().isBadRequest)
    }

    @Test
    fun `the addresses are the ones the pages already answered to`() {
        // Every link anybody has ever shared keeps working.
        mvc.perform(get("/esports/games"))
            .andExpect(jsonPath("$[?(@.game == 'CS2')].slug").value("counter-strike-2"))
            .andExpect(jsonPath("$[?(@.game == 'ROCKET_LEAGUE')].slug").value("rocketleague"))
            .andExpect(jsonPath("$[?(@.game == 'LEAGUE_OF_LEGENDS')].slug").value("league-of-legends"))
    }

    @Test
    fun `a game whose page was never routed to has an address now`() {
        // Trackmania had a component with copy written for it and nothing routing to it.
        mvc.perform(get("/esports/games"))
            .andExpect(jsonPath("$[?(@.game == 'TRACKMANIA')].slug").value("trackmania"))
            .andExpect(jsonPath("$[?(@.game == 'TRACKMANIA')].intro").isNotEmpty)
    }

    @Test
    fun `a game no longer fielded says so, and keeps its page`() {
        mvc.perform(get("/esports/games"))
            .andExpect(jsonPath("$[?(@.game == 'CSGO')].fielded").value(false))
            .andExpect(jsonPath("$[?(@.game == 'SMASH')].fielded").value(false))
            .andExpect(jsonPath("$[?(@.game == 'VALORANT')].fielded").value(true))
    }

    @Test
    fun `a game carries the name the pages print`() {
        mvc.perform(get("/esports/games"))
            .andExpect(jsonPath("$[?(@.game == 'LEAGUE_OF_LEGENDS')].name").value("League of Legends"))
            .andExpect(jsonPath("$[?(@.game == 'CSGO')].name").value("CS:GO"))
            .andExpect(jsonPath("$[?(@.game == 'SMASH')].name").value("Super Smash Bros."))
    }

    @Test
    fun `a game carries the art it is drawn with`() {
        mvc.perform(get("/esports/games"))
            .andExpect(jsonPath("$[?(@.game == 'VALORANT')].accent").value("#ff4655"))
            .andExpect(jsonPath("$[?(@.game == 'VALORANT')].mark").value("valorant.png"))
            .andExpect(jsonPath("$[?(@.game == 'VALORANT')].banner").value("valorantesports1.jpg"))
    }

    @Test
    fun `a game nobody has drawn art for says so rather than inventing any`() {
        // The island reads such a game on the association's own colour; it does not go missing.
        mvc.perform(get("/esports/games"))
            .andExpect(jsonPath("$[?(@.game == 'TRACKMANIA')].name").value("Trackmania"))
            .andExpect(jsonPath("$[?(@.game == 'TRACKMANIA')].accent").doesNotExist())
            .andExpect(jsonPath("$[?(@.game == 'TRACKMANIA')].mark").doesNotExist())
    }

    /**
     * The enum made an unknown code unrepresentable in Kotlin, and the database was told
     * nothing. Once a game is a row rather than a compiled constant that stops being true, so
     * the tie is stated where the rows live. Written natively because a well-typed caller
     * cannot express the code these reject; each states the accepted case alongside the
     * rejected one, so a statement that is simply malformed cannot read as the tie holding.
     */
    @Test
    fun `a team cannot name a game that does not exist`() {
        insertTeamNamed("VALORANT", "Blueshell Firsts")

        assertThatThrownBy { insertTeamNamed("PONG", "Table Tennis Firsts") }
            .hasMessageContaining("fk_team_game")
    }

    @Test
    fun `a member's game account cannot name a game that does not exist`() {
        val member = createUserWithRole(Role.MEMBER)
        insertAccountFor(member.id!!, "VALORANT")

        assertThatThrownBy { insertAccountFor(member.id!!, "PONG") }
            .hasMessageContaining("fk_user_game_account_game")
    }

    private fun insertTeamNamed(game: String, name: String) = transactionTemplate.execute {
        entityManager.createNativeQuery("INSERT INTO team (game, name) VALUES (:game, :name)")
            .setParameter("game", game)
            .setParameter("name", name)
            .executeUpdate()
        entityManager.flush()
    }

    private fun insertAccountFor(userId: Long, game: String) = transactionTemplate.execute {
        entityManager.createNativeQuery(
            "INSERT INTO user_game_account (user_id, game, handle) VALUES (:user, :game, 'paddler')",
        )
            .setParameter("user", userId)
            .setParameter("game", game)
            .executeUpdate()
        entityManager.flush()
    }

    @Test
    fun `the board can rewrite what a game says about itself`() {
        val board = createUserWithRole(Role.BOARD)

        mvc.perform(
            put("/esports/games/{game}", "GEOGUESSR")
                .with(bearer(board))
                .contentType(MediaType.APPLICATION_JSON)
                .content("""{"slug":"geoguessr","intro":"Guessing, competitively.","sortIndex":5,"fielded":true}"""),
        )
            .andExpect(status().isOk)
            .andExpect(jsonPath("$.intro").value("Guessing, competitively."))

        mvc.perform(get("/esports/games"))
            .andExpect(jsonPath("$[?(@.game == 'GEOGUESSR')].intro").value("Guessing, competitively."))
    }

    @Test
    fun `two games cannot answer to the same address`() {
        val board = createUserWithRole(Role.BOARD)

        mvc.perform(
            put("/esports/games/{game}", "SMASH")
                .with(bearer(board))
                .contentType(MediaType.APPLICATION_JSON)
                .content("""{"slug":"valorant","intro":null,"sortIndex":8,"fielded":false}"""),
        )
            .andExpect(status().isConflict)
    }

    @Test
    fun `a game may keep its own address while something else about it changes`() {
        val board = createUserWithRole(Role.BOARD)

        // Saving without changing the address must not read as a clash with itself.
        mvc.perform(
            put("/esports/games/{game}", "TRACKMANIA")
                .with(bearer(board))
                .contentType(MediaType.APPLICATION_JSON)
                .content("""{"slug":"trackmania","intro":"Driving, fast.","sortIndex":6,"fielded":true}"""),
        )
            .andExpect(status().isOk)
    }

    @Test
    fun `the board adds a game the association has started playing`() {
        val board = createUserWithRole(Role.BOARD)

        mvc.perform(
            post("/esports/games")
                .with(bearer(board))
                .contentType(MediaType.APPLICATION_JSON)
                .content("""{"name":"Rocket League 2","slug":"rocket-league-2"}"""),
        )
            .andExpect(status().isCreated)
            .andExpect(jsonPath("$.name").value("Rocket League 2"))
            .andExpect(jsonPath("$.slug").value("rocket-league-2"))
            // Its code is taken from its name: the identity everything else points at.
            .andExpect(jsonPath("$.game").value("ROCKET_LEAGUE_2"))
            // Nobody has drawn it anything, so it reads on the island's own colour.
            .andExpect(jsonPath("$.accent").doesNotExist())
            .andExpect(jsonPath("$.fielded").value(true))

        mvc.perform(get("/esports/games"))
            .andExpect(jsonPath("$[?(@.slug == 'rocket-league-2')].name").value("Rocket League 2"))
    }

    @Test
    fun `a game added this way can have a team written for it straight away`() {
        val board = createUserWithRole(Role.BOARD)
        mvc.perform(
            post("/esports/games").with(bearer(board))
                .contentType(MediaType.APPLICATION_JSON)
                .content("""{"name":"Pong","slug":"pong"}"""),
        ).andExpect(status().isCreated)

        // The code the api answered with is a real game now, which the foreign key agrees with.
        mvc.perform(
            post("/esports/teams").with(bearer(board))
                .contentType(MediaType.APPLICATION_JSON)
                .content("""{"game":"PONG","name":"BS Paddlers"}"""),
        )
            .andExpect(status().isCreated)
            .andExpect(jsonPath("$.game").value("PONG"))
    }

    @Test
    fun `an address another game already claims is refused with a reason`() {
        val board = createUserWithRole(Role.BOARD)

        mvc.perform(
            post("/esports/games").with(bearer(board))
                .contentType(MediaType.APPLICATION_JSON)
                .content("""{"name":"Valorant Two","slug":"valorant"}"""),
        )
            .andExpect(status().isConflict)
            .andExpect(jsonPath("$.detail").value(org.hamcrest.Matchers.containsString("Valorant")))
    }

    @Test
    fun `a game the association already knows is refused rather than added twice`() {
        val board = createUserWithRole(Role.BOARD)

        mvc.perform(
            post("/esports/games").with(bearer(board))
                .contentType(MediaType.APPLICATION_JSON)
                .content("""{"name":"Valorant","slug":"valorant-again"}"""),
        )
            .andExpect(status().isConflict)
    }

    @Test
    fun `an address is tidied into one somebody can be sent to`() {
        val board = createUserWithRole(Role.BOARD)

        mvc.perform(
            post("/esports/games").with(bearer(board))
                .contentType(MediaType.APPLICATION_JSON)
                .content("""{"name":"Age of Empires II","slug":"  Age Of Empires II  "}"""),
        )
            .andExpect(status().isCreated)
            .andExpect(jsonPath("$.slug").value("age-of-empires-ii"))
            .andExpect(jsonPath("$.game").value("AGE_OF_EMPIRES_II"))
    }

    @Test
    fun `a game cannot claim the index's own address`() {
        val board = createUserWithRole(Role.BOARD)

        // It would have a record and no page, because that address is the index's.
        mvc.perform(
            post("/esports/games").with(bearer(board))
                .contentType(MediaType.APPLICATION_JSON)
                .content("""{"name":"Competitive Scene","slug":"competitive-scene"}"""),
        )
            .andExpect(status().isConflict)
    }

    @Test
    fun `a member cannot add a game`() {
        val member = createUserWithRole(Role.MEMBER)

        mvc.perform(
            post("/esports/games").with(bearer(member))
                .contentType(MediaType.APPLICATION_JSON)
                .content("""{"name":"Pong","slug":"pong"}"""),
        )
            .andExpect(status().isForbidden)
    }

    @Test
    fun `a member cannot rewrite a game's page`() {
        val member = createUserWithRole(Role.MEMBER)

        mvc.perform(
            put("/esports/games/{game}", "VALORANT")
                .with(bearer(member))
                .contentType(MediaType.APPLICATION_JSON)
                .content("""{"slug":"valorant","intro":"Mine now.","sortIndex":1,"fielded":true}"""),
        )
            .andExpect(status().isForbidden)
    }

    @Test
    fun `an anonymous visitor may read the pages but not change one`() {
        mvc.perform(get("/esports/games")).andExpect(status().isOk)
        mvc.perform(
            put("/esports/games/{game}", "VALORANT")
                .contentType(MediaType.APPLICATION_JSON)
                .content("""{"slug":"valorant","intro":null,"sortIndex":1,"fielded":true}"""),
        )
            .andExpect(status().isUnauthorized)
    }
}

package net.blueshell.api.esports.persistence

import net.blueshell.api.shared.enums.Game
import net.blueshell.api.shared.enums.Role
import net.blueshell.api.testsupport.UserTestSupport
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.Test
import org.springframework.beans.factory.annotation.Autowired
import org.springframework.boot.test.context.SpringBootTest
import org.springframework.http.MediaType
import org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get
import org.springframework.test.web.servlet.request.MockMvcRequestBuilders.put
import org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath
import org.springframework.test.web.servlet.result.MockMvcResultMatchers.status

/**
 * A game's presentation: the address it answers to, what is said about it, where it sits, and
 * whether the association still fields a team in it. All four lived in the frontend, where a
 * change to any of them was a deploy.
 */
@SpringBootTest
class GamePageIT : UserTestSupport() {

    @Autowired private lateinit var pages: GamePageRepository

    /**
     * The suite resets the database between cases, which takes the migration's own rows with
     * it, so each case states the presentation it is about. The values are the ones the
     * migration seeds: the addresses the router already answered to, Trackmania given the one
     * it never had, and the two retired games recorded as no longer fielded.
     */
    @BeforeEach
    fun seedGamePages() {
        if (pages.findByGame(Game.VALORANT) != null) return
        listOf(
            GamePage(Game.VALORANT, "valorant", "Shooters, and plenty of them.", 1, true),
            GamePage(Game.CS2, "counter-strike-2", "Those sweet headshots.", 2, true),
            GamePage(Game.LEAGUE_OF_LEGENDS, "league-of-legends", "A special place.", 3, true),
            GamePage(Game.ROCKET_LEAGUE, "rocketleague", "Football, with rocket cars.", 4, true),
            GamePage(Game.GEOGUESSR, "geoguessr", "Guessing where.", 5, true),
            GamePage(Game.TRACKMANIA, "trackmania", "Driving, fast.", 6, true),
            GamePage(Game.CSGO, "counter-strike-global-offensive", null, 7, false),
            GamePage(Game.SMASH, "super-smash-bros", null, 8, false),
        ).forEach { pages.save(it) }
    }

    @Test
    fun `every game has a page, in the order they are shown`() {
        mvc.perform(get("/esports/games"))
            .andExpect(status().isOk)
            .andExpect(jsonPath("$.length()").value(Game.entries.size))
            .andExpect(jsonPath("$[0].game").value("VALORANT"))
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
            .andExpect(jsonPath("$[?(@.game == 'TRACKMANIA')].intro").value("Driving, fast."))
    }

    @Test
    fun `a game no longer fielded says so, and keeps its page`() {
        mvc.perform(get("/esports/games"))
            .andExpect(jsonPath("$[?(@.game == 'CSGO')].fielded").value(false))
            .andExpect(jsonPath("$[?(@.game == 'SMASH')].fielded").value(false))
            .andExpect(jsonPath("$[?(@.game == 'VALORANT')].fielded").value(true))
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

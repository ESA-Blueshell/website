package net.blueshell.api.esports.web

import net.blueshell.api.esports.domain.GameAddressBlank
import net.blueshell.api.esports.domain.GameNameBlank
import net.blueshell.api.esports.domain.GamePageService
import net.blueshell.api.esports.domain.SeasonGameService
import net.blueshell.api.esports.domain.TeamSeasonService
import net.blueshell.api.esports.persistence.Season
import net.blueshell.api.esports.persistence.SeasonRepository
import net.blueshell.api.esports.persistence.Team
import net.blueshell.api.esports.persistence.TeamRepository
import net.blueshell.api.shared.enums.Role
import net.blueshell.api.testsupport.UserTestSupport
import org.assertj.core.api.Assertions.assertThatThrownBy
import org.junit.jupiter.api.Test
import org.springframework.beans.factory.annotation.Autowired
import org.springframework.boot.test.context.SpringBootTest
import org.springframework.http.MediaType
import org.springframework.test.web.servlet.request.MockMvcRequestBuilders.delete
import org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get
import org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post
import org.springframework.test.web.servlet.request.MockMvcRequestBuilders.put
import org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath
import org.springframework.test.web.servlet.result.MockMvcResultMatchers.status
import java.time.LocalDate

/**
 * What a refused esports write answers with.
 *
 * The api used to answer a finished English sentence, which the frontend printed. It answers a
 * code and the facts about the refusal now, and the frontend composes the sentence — so these
 * assert the code and the properties rather than `detail`, which is fixed per code and is the
 * part no client reads. See ADR-026.
 */
@SpringBootTest
class EsportsRefusalIT : UserTestSupport() {
    @Autowired private lateinit var teams: TeamRepository

    @Autowired private lateinit var seasons: SeasonRepository

    @Autowired private lateinit var fielded: TeamSeasonService

    @Autowired private lateinit var entered: SeasonGameService

    @Autowired private lateinit var games: GamePageService

    private fun aSeason(): Season {
        val unique = System.nanoTime()
        return seasons.save(
            Season(
                name = "Season $unique",
                startDate = LocalDate.of(2050, 9, 1),
                endDate = LocalDate.of(2051, 1, 31),
            ),
        )
    }

    @Test
    fun `a code naming no game answers UnknownGameCode and the code it was given`() {
        mvc.perform(get("/esports/games/{game}", "PONG"))
            .andExpect(status().isBadRequest)
            .andExpect(jsonPath("$.code").value("UnknownGameCode"))
            .andExpect(jsonPath("$.gameCode").value("PONG"))
            // Fixed per code: it names no game, so it is the same sentence every time.
            .andExpect(jsonPath("$.detail").value("No game has that code."))
    }

    /**
     * At the service rather than over http, because `@NotBlank` on the request refuses a blank
     * name before the service sees it. The guard behind it is not dead — it is what answers a
     * caller that is not the controller — but no dialog can reach it, so no reader meets its
     * copy and the contract test belongs at the seam that can be provoked.
     */
    @Test
    fun `a game with no name is refused at the service, behind the request's own validation`() {
        assertThatThrownBy { games.create(name = "   ", slug = "pong") }
            .isInstanceOf(GameNameBlank::class.java)
            .extracting { (it as GameNameBlank).code }
            .isEqualTo("GameNameBlank")
    }

    @Test
    fun `a name a code cannot be made from answers GameNameUnusable and the name given`() {
        val board = createUserWithRole(Role.BOARD)

        mvc.perform(
            post("/esports/games")
                .with(bearer(board))
                .contentType(MediaType.APPLICATION_JSON)
                .content("""{"name":"!!!","slug":"pong"}"""),
        )
            .andExpect(status().isBadRequest)
            .andExpect(jsonPath("$.code").value("GameNameUnusable"))
            .andExpect(jsonPath("$.given").value("!!!"))
    }

    @Test
    fun `a game that exists answers GameAlreadyExists and what it is called`() {
        val board = createUserWithRole(Role.BOARD)

        mvc.perform(
            post("/esports/games")
                .with(bearer(board))
                .contentType(MediaType.APPLICATION_JSON)
                .content("""{"name":"Valorant","slug":"valorant-again"}"""),
        )
            .andExpect(status().isConflict)
            .andExpect(jsonPath("$.code").value("GameAlreadyExists"))
            .andExpect(jsonPath("$.gameName").value("Valorant"))
    }

    @Test
    fun `a game holding history answers GameHoldsHistory and the counts, not a sentence`() {
        val board = createUserWithRole(Role.BOARD)
        // The suite blanks the fieldings between cases, so the history is made here rather
        // than leaned on: a game holds something because a team plays it.
        val season = aSeason()
        val team = teams.save(Team(name = "BS Historic ${System.nanoTime()}"))
        entered.enter(season.id!!, "VALORANT")
        fielded.field(team.id!!, "VALORANT", season.id!!)

        mvc.perform(delete("/esports/games/{game}", "VALORANT").with(bearer(board)))
            .andExpect(status().isConflict)
            .andExpect(jsonPath("$.code").value("GameHoldsHistory"))
            .andExpect(jsonPath("$.gameName").value("Valorant"))
            // Counts, so how they are pluralised and joined stays the frontend's.
            .andExpect(jsonPath("$.teams").isNumber)
            .andExpect(jsonPath("$.players").isNumber)
            .andExpect(jsonPath("$.detail").value("That game cannot be removed."))
    }

    @Test
    fun `a game still fielded in a season answers GameFieldedInSeason and how many teams`() {
        val board = createUserWithRole(Role.BOARD)
        val season = aSeason()
        val team = teams.save(Team(name = "BS Refused ${System.nanoTime()}"))
        entered.enter(season.id!!, "TRACKMANIA")
        fielded.field(team.id!!, "TRACKMANIA", season.id!!)

        mvc.perform(
            delete("/esports/seasons/{seasonId}/games/{game}", season.id, "TRACKMANIA")
                .with(bearer(board)),
        )
            .andExpect(status().isConflict)
            .andExpect(jsonPath("$.code").value("GameFieldedInSeason"))
            .andExpect(jsonPath("$.gameName").value("Trackmania"))
            .andExpect(jsonPath("$.teams").value(1))
    }

    /** Behind `@NotBlank` on the request too, for the same reason as a blank name. */
    @Test
    fun `a page with no address is refused at the service`() {
        assertThatThrownBy { games.create(name = "Pong", slug = "   ") }
            .isInstanceOf(GameAddressBlank::class.java)
            .extracting { (it as GameAddressBlank).code }
            .isEqualTo("GameAddressBlank")
    }

    @Test
    fun `the index's own address answers AddressReserved and the address asked for`() {
        val board = createUserWithRole(Role.BOARD)

        mvc.perform(
            post("/esports/games")
                .with(bearer(board))
                .contentType(MediaType.APPLICATION_JSON)
                .content("""{"name":"Pong","slug":"competitive-scene"}"""),
        )
            .andExpect(status().isConflict)
            .andExpect(jsonPath("$.code").value("AddressReserved"))
            .andExpect(jsonPath("$.address").value("competitive-scene"))
    }

    @Test
    fun `an address another game holds answers AddressTaken, that game and the address`() {
        val board = createUserWithRole(Role.BOARD)

        mvc.perform(
            put("/esports/games/{game}", "SMASH")
                .with(bearer(board))
                .contentType(MediaType.APPLICATION_JSON)
                .content(
                    """{"name":"Super Smash Bros.","slug":"valorant","intro":null,"sortIndex":8}""",
                ),
        )
            .andExpect(status().isConflict)
            .andExpect(jsonPath("$.code").value("AddressTaken"))
            .andExpect(jsonPath("$.gameName").value("Valorant"))
            .andExpect(jsonPath("$.address").value("valorant"))
    }

    @Test
    fun `overlapping season dates answer SeasonDatesOverlap and the season clashed with`() {
        val board = createUserWithRole(Role.BOARD)
        val held = aSeason()

        mvc.perform(
            post("/esports/seasons")
                .with(bearer(board))
                .contentType(MediaType.APPLICATION_JSON)
                .content(
                    """{"name":"Clashing","startDate":"2050-11-01","endDate":"2051-03-31"}""",
                ),
        )
            .andExpect(status().isBadRequest)
            .andExpect(jsonPath("$.code").value("SeasonDatesOverlap"))
            .andExpect(jsonPath("$.seasonName").value(held.name))
    }

    @Test
    fun `a season ending before it starts answers SeasonEndsBeforeStart`() {
        val board = createUserWithRole(Role.BOARD)

        mvc.perform(
            post("/esports/seasons")
                .with(bearer(board))
                .contentType(MediaType.APPLICATION_JSON)
                .content(
                    """{"name":"Backwards","startDate":"2060-09-01","endDate":"2060-08-31"}""",
                ),
        )
            .andExpect(status().isBadRequest)
            .andExpect(jsonPath("$.code").value("SeasonEndsBeforeStart"))
    }

    @Test
    fun `a picture nothing stored answers PictureNotStored`() {
        val board = createUserWithRole(Role.BOARD)

        mvc.perform(
            put("/esports/games/{game}", "TRACKMANIA")
                .with(bearer(board))
                .contentType(MediaType.APPLICATION_JSON)
                .content(
                    """{"name":"Trackmania","slug":"trackmania","intro":null,"sortIndex":6,""" +
                        """"banner":"game-banner/nothing-is-stored-here.webp"}""",
                ),
        )
            .andExpect(status().isBadRequest)
            .andExpect(jsonPath("$.code").value("PictureNotStored"))
    }

    @Test
    fun `a row that is not there keeps its sentence and carries no code`() {
        val board = createUserWithRole(Role.BOARD)

        // A dialog cannot provoke this: reaching it takes a hand-built request naming a row
        // that does not exist, so there is no copy for a reader to meet.
        mvc.perform(delete("/esports/seasons/{id}", 9_999_999L).with(bearer(board)))
            .andExpect(status().isNotFound)
            .andExpect(jsonPath("$.code").doesNotExist())
    }
}

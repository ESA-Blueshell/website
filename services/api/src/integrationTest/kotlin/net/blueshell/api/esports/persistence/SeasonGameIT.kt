package net.blueshell.api.esports.persistence

import net.blueshell.api.esports.domain.SeasonGameService
import net.blueshell.api.esports.domain.TeamSeasonService
import net.blueshell.api.shared.enums.Role
import net.blueshell.api.testsupport.UserTestSupport
import org.junit.jupiter.api.Test
import org.springframework.beans.factory.annotation.Autowired
import org.springframework.boot.test.context.SpringBootTest
import org.springframework.test.web.servlet.request.MockMvcRequestBuilders.delete
import org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get
import org.springframework.test.web.servlet.request.MockMvcRequestBuilders.put
import org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath
import org.springframework.test.web.servlet.result.MockMvcResultMatchers.status
import java.time.LocalDate

/**
 * A game runs in a season whether or not anybody has been fielded in it yet.
 *
 * The board settles which games the association plays when the season is planned; the squads form
 * over the weeks after. What that leaves is a season the board can see the whole of and a visitor
 * sees only the finished parts of, and the split turns on who is asking — so it is asserted here,
 * where a request is made as somebody, rather than anywhere a template could fake it.
 */
@SpringBootTest
class SeasonGameIT : UserTestSupport() {
    @Autowired private lateinit var entered: SeasonGameService

    @Autowired private lateinit var fielded: TeamSeasonService

    @Autowired private lateinit var seasons: SeasonRepository

    @Autowired private lateinit var teams: TeamRepository

    private var counter = 0

    private fun season(): Season {
        counter += 1
        return seasons.save(
            Season(
                name = "Season $counter ${System.nanoTime()}",
                startDate = LocalDate.of(2040, 9, 1).plusYears(counter.toLong()),
                endDate = LocalDate.of(2041, 1, 31).plusYears(counter.toLong()),
            ),
        )
    }

    private fun fieldOne(game: String, season: Season): Team {
        val team = teams.save(Team(name = "Team ${System.nanoTime()}"))
        entered.enter(season.id!!, game)
        fielded.field(team.id!!, game, season.id!!)
        return team
    }

    @Test
    fun `a game entered with nobody fielded is absent for a visitor`() {
        val season = season()
        entered.enter(season.id!!, "TRACKMANIA")

        mvc.perform(get("/esports/seasons/{id}/games", season.id))
            .andExpect(status().isOk)
            .andExpect(jsonPath("$.length()").value(0))
    }

    @Test
    fun `the board sees it, and is told it is not public`() {
        val board = createUserWithRole(Role.BOARD)
        val season = season()
        entered.enter(season.id!!, "TRACKMANIA")

        mvc.perform(get("/esports/seasons/{id}/games", season.id).with(bearer(board)))
            .andExpect(status().isOk)
            .andExpect(jsonPath("$[?(@.game == 'TRACKMANIA')].public").value(false))
            .andExpect(jsonPath("$[?(@.game == 'TRACKMANIA')].teams.length()").value(0))
    }

    @Test
    fun `a game with a team is public, and a visitor sees it`() {
        val season = season()
        fieldOne("TRACKMANIA", season)

        mvc.perform(get("/esports/seasons/{id}/games", season.id))
            .andExpect(status().isOk)
            .andExpect(jsonPath("$[?(@.game == 'TRACKMANIA')].public").value(true))
    }

    @Test
    fun `dropping the last team leaves the game in the season, board-only`() {
        val board = createUserWithRole(Role.BOARD)
        val season = season()
        val team = fieldOne("TRACKMANIA", season)

        fielded.unfield(team.id!!, "TRACKMANIA", season.id!!)

        // "We entered it and fielded nobody" is a fact worth keeping, and correcting it is a
        // separate act rather than something that happens as a side effect of dropping a team.
        mvc.perform(get("/esports/seasons/{id}/games", season.id))
            .andExpect(jsonPath("$.length()").value(0))
        mvc.perform(get("/esports/seasons/{id}/games", season.id).with(bearer(board)))
            .andExpect(jsonPath("$[?(@.game == 'TRACKMANIA')].public").value(false))
    }

    @Test
    fun `taking a game out of a season is refused while it still has teams`() {
        val board = createUserWithRole(Role.BOARD)
        val season = season()
        fieldOne("TRACKMANIA", season)

        mvc.perform(delete("/esports/seasons/{id}/games/{game}", season.id, "TRACKMANIA").with(bearer(board)))
            .andExpect(status().isConflict)
            .andExpect(jsonPath("$.code").value("GameFieldedInSeason"))
            .andExpect(jsonPath("$.teams").value(1))
    }

    @Test
    fun `taking an empty game out of a season removes it`() {
        val board = createUserWithRole(Role.BOARD)
        val season = season()
        entered.enter(season.id!!, "TRACKMANIA")

        mvc.perform(delete("/esports/seasons/{id}/games/{game}", season.id, "TRACKMANIA").with(bearer(board)))
            .andExpect(status().isNoContent)

        mvc.perform(get("/esports/seasons/{id}/games", season.id).with(bearer(board)))
            .andExpect(jsonPath("$.length()").value(0))
    }

    @Test
    fun `a game entered again after being taken out is entered once, not twice`() {
        val board = createUserWithRole(Role.BOARD)
        val season = season()
        entered.enter(season.id!!, "TRACKMANIA")
        entered.leave(season.id!!, "TRACKMANIA")

        mvc.perform(put("/esports/seasons/{id}/games/{game}", season.id, "TRACKMANIA").with(bearer(board)))
            .andExpect(status().isOk)
            .andExpect(jsonPath("$.game").value("TRACKMANIA"))

        mvc.perform(get("/esports/seasons/{id}/games", season.id).with(bearer(board)))
            .andExpect(jsonPath("$.length()").value(1))
    }

    @Test
    fun `a member may not enter a game in a season`() {
        val member = createUserWithRole(Role.MEMBER)
        val season = season()

        mvc.perform(put("/esports/seasons/{id}/games/{game}", season.id, "TRACKMANIA").with(bearer(member)))
            .andExpect(status().isForbidden)
    }
}

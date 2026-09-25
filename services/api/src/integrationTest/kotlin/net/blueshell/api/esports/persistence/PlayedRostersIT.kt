package net.blueshell.api.esports.persistence

import net.blueshell.api.esports.domain.TeamSeasonService
import net.blueshell.api.shared.enums.Role
import net.blueshell.api.shared.enums.TeamRole
import net.blueshell.api.testsupport.UserTestSupport
import org.junit.jupiter.api.Test
import org.springframework.beans.factory.annotation.Autowired
import org.springframework.boot.test.context.SpringBootTest
import org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get
import org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath
import org.springframework.test.web.servlet.result.MockMvcResultMatchers.status
import java.time.LocalDate

/** The seasons and teams somebody played for, read off the rosters they are linked on. */
@SpringBootTest
class PlayedRostersIT : UserTestSupport() {
    @Autowired
    private lateinit var seasons: SeasonRepository

    @Autowired
    private lateinit var teams: TeamRepository

    @Autowired
    private lateinit var entries: TeamRosterEntryRepository

    @Autowired
    private lateinit var fielded: TeamSeasonService

    private fun seat(
        userId: Long?,
        game: String,
        season: Season,
        team: Team,
        role: TeamRole = TeamRole.PLAYER,
        title: String? = null,
    ) {
        val fielding = fielded.field(team.id!!, game, season.id!!)
        entries.save(
            TeamRosterEntry(teamSeason = fielding, handle = "h${System.nanoTime()}", teamRole = role, userId = userId, roleTitle = title),
        )
    }

    private fun season(start: LocalDate) =
        seasons.save(Season(name = "Season ${System.nanoTime()}", startDate = start, endDate = start.plusMonths(4)))

    @Test
    fun `lists every roster spot a person held, newest season first, and nobody else's`() {
        val player = createUserWithRole(Role.MEMBER)
        val other = createUserWithRole(Role.MEMBER)
        val older = season(LocalDate.of(2024, 9, 1))
        val newer = season(LocalDate.of(2025, 9, 1))
        val squad = teams.save(Team(name = "Team ${System.nanoTime()}"))
        seat(player.id, "VALORANT", older, squad)
        seat(player.id, "CS2", newer, squad, TeamRole.SUBSTITUTE, "In-game leader")
        seat(other.id, "CS2", newer, squad)
        seat(null, "VALORANT", newer, squad)

        mvc
            .perform(get("/users/{userId}/rosters", player.id).with(signedIn(player)))
            .andExpect(status().isOk)
            .andExpect(jsonPath("$.length()").value(2))
            .andExpect(jsonPath("$[0].game").value("CS2"))
            .andExpect(jsonPath("$[0].seasonName").value(newer.name))
            .andExpect(jsonPath("$[0].teamName").value(squad.name))
            .andExpect(jsonPath("$[0].role").value("SUBSTITUTE"))
            .andExpect(jsonPath("$[0].roleTitle").value("In-game leader"))
            .andExpect(jsonPath("$[1].seasonId").value(older.id!!))
    }

    @Test
    fun `somebody else's history is theirs to read, and the board's`() {
        val player = createUserWithRole(Role.MEMBER)
        val other = createUserWithRole(Role.MEMBER)

        mvc.perform(get("/users/{userId}/rosters", player.id).with(signedIn(other))).andExpect(status().isForbidden)
        mvc.perform(get("/users/{userId}/rosters", player.id).with(signedIn(createUserWithRole(Role.BOARD)))).andExpect(status().isOk)
    }
}

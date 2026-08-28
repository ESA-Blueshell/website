package net.blueshell.api.esports.persistence

import net.blueshell.api.shared.enums.Game
import net.blueshell.api.shared.enums.Role
import net.blueshell.api.shared.enums.TeamRole
import net.blueshell.api.testsupport.UserTestSupport
import org.assertj.core.api.Assertions.assertThat
import org.junit.jupiter.api.Test
import org.springframework.beans.factory.annotation.Autowired
import org.springframework.boot.test.context.SpringBootTest
import org.springframework.http.MediaType
import org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post
import org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath
import org.springframework.test.web.servlet.result.MockMvcResultMatchers.status
import java.time.LocalDate
import net.blueshell.api.esports.api.TeamRosterService
import net.blueshell.api.esports.domain.EsportsPageQueryService

/**
 * A roster entry said whether somebody was a player, a substitute or a coach, and nothing
 * else. A captain, an in-game leader and a jungler are all PLAYER, so the page had no way to
 * say what any of them actually did, and nowhere to put the sentence a visitor remembers.
 */
@SpringBootTest
class RosterEntryDetailIT : UserTestSupport() {
    @Autowired private lateinit var rosters: TeamRosterService

    @Autowired private lateinit var page: EsportsPageQueryService

    @Autowired private lateinit var seasons: SeasonRepository

    @Autowired private lateinit var teams: TeamRepository

    private fun season(): Season = seasons.save(
        Season(
            name = "Season ${System.nanoTime()}",
            startDate = LocalDate.of(2050, 9, 1),
            endDate = LocalDate.of(2051, 1, 31),
        ),
    )

    private fun team(): Team = teams.save(Team(game = Game.TRACKMANIA, name = "BS Detail ${System.nanoTime()}"))

    @Test
    fun `a roster entry carries what somebody did and a caption about them`() {
        val season = season()
        val team = team()

        val entry = rosters.add(
            team.id!!, season.id!!, "driver", TeamRole.PLAYER, null, null,
            roleTitle = "Captain",
            description = "Holds the **middle** together.",
        )

        assertThat(entry.roleTitle).isEqualTo("Captain")
        assertThat(entry.description).isEqualTo("Holds the **middle** together.")
    }

    @Test
    fun `both reach the public page, where the roster is read`() {
        val season = season()
        val team = team()
        rosters.add(
            team.id!!, season.id!!, "driver", TeamRole.PLAYER, null, null,
            roleTitle = "In-game leader",
            description = "Calls the rounds.",
        )

        val member = page.page(Game.TRACKMANIA, season.id).teams.single().members.single()

        assertThat(member.roleTitle).isEqualTo("In-game leader")
        assertThat(member.description).isEqualTo("Calls the rounds.")
        // The enum is still what the roster is grouped by; the words are decoration on top.
        assertThat(member.role).isEqualTo(TeamRole.PLAYER)
    }

    @Test
    fun `saying nothing leaves both empty rather than blank`() {
        val season = season()
        val team = team()

        val entry = rosters.add(
            team.id!!, season.id!!, "quiet", TeamRole.PLAYER, null, null,
            roleTitle = "   ",
            description = "",
        )

        assertThat(entry.roleTitle).isNull()
        assertThat(entry.description).isNull()
    }

    @Test
    fun `a description longer than a caption is refused on the way in`() {
        val board = createUserWithRole(Role.BOARD)
        val season = season()
        val team = team()

        mvc.perform(
            post("/esports/teams/{teamId}/roster", team.id)
                .with(bearer(board))
                .contentType(MediaType.APPLICATION_JSON)
                .content(
                    """
                    {"seasonId":${season.id},"handle":"windy","role":"PLAYER","description":"${"a".repeat(281)}"}
                    """.trimIndent(),
                ),
        ).andExpect(status().isBadRequest)
    }

    @Test
    fun `a description of exactly the cap is accepted`() {
        val board = createUserWithRole(Role.BOARD)
        val season = season()
        val team = team()

        mvc.perform(
            post("/esports/teams/{teamId}/roster", team.id)
                .with(bearer(board))
                .contentType(MediaType.APPLICATION_JSON)
                .content(
                    """
                    {"seasonId":${season.id},"handle":"exact","role":"PLAYER","description":"${"a".repeat(280)}"}
                    """.trimIndent(),
                ),
        )
            .andExpect(status().isCreated)
            .andExpect(jsonPath("$.description").value("a".repeat(280)))
    }

    @Test
    fun `changing one season's words leaves the same team's other seasons alone`() {
        val earlier = season()
        val later = seasons.save(
            Season(
                name = "Later ${System.nanoTime()}",
                startDate = LocalDate.of(2052, 2, 1),
                endDate = LocalDate.of(2052, 8, 31),
            ),
        )
        val team = team()
        val first = rosters.add(team.id!!, earlier.id!!, "driver", TeamRole.PLAYER, null, null, roleTitle = "Captain")
        rosters.add(team.id!!, later.id!!, "driver", TeamRole.PLAYER, null, null, roleTitle = "Coach")

        rosters.update(
            id = first.id!!,
            handle = "driver",
            role = TeamRole.PLAYER,
            displayName = null,
            sortIndex = 0,
            roleTitle = "Stand-in captain",
        )

        val laterEntry = rosters.findByTeamAndSeason(team.id!!, later.id!!).single()
        assertThat(laterEntry.roleTitle).isEqualTo("Coach")
    }
}

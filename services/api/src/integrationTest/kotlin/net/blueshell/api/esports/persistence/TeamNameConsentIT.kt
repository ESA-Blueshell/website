package net.blueshell.api.esports.persistence

import net.blueshell.api.esports.domain.TeamSeasonService
import net.blueshell.api.shared.enums.Role
import net.blueshell.api.shared.enums.TeamRole
import net.blueshell.api.testsupport.UserTestSupport
import net.blueshell.api.user.persistence.User
import org.junit.jupiter.api.Test
import org.springframework.beans.factory.annotation.Autowired
import org.springframework.boot.test.context.SpringBootTest
import org.springframework.http.MediaType
import org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get
import org.springframework.test.web.servlet.request.MockMvcRequestBuilders.put
import org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath
import org.springframework.test.web.servlet.result.MockMvcResultMatchers.status
import java.time.LocalDate

/**
 * Whether a roster shows a real name is the person's own decision, member or not, and that
 * question is asked of every entry the public read carries.
 */
@SpringBootTest
class TeamNameConsentIT : UserTestSupport() {
    @Autowired
    private lateinit var seasons: SeasonRepository

    @Autowired
    private lateinit var teams: TeamRepository

    @Autowired
    private lateinit var entries: TeamRosterEntryRepository

    @Autowired
    private lateinit var fielded: TeamSeasonService

    private fun season(): Season =
        seasons.save(
            Season(
                name = "Season ${System.nanoTime()}",
                startDate = LocalDate.of(2025, 9, 1),
                endDate = LocalDate.of(2026, 1, 31),
            ),
        )

    /** A team and the game it is fielded in. The fielding names the game, not the team. */
    private data class Squad(
        val team: Team,
        val game: String,
    ) {
        val name: String get() = team.name
    }

    private fun team(game: String): Squad = Squad(teams.save(Team(name = "Team ${System.nanoTime()}")), game)

    private fun seat(
        squad: Squad,
        season: Season,
        handle: String,
        userId: Long?,
    ): TeamRosterEntry {
        // A line-up hangs off the fielding, so the fielding is what the entry is written
        // against — there is nothing to attach one to until it exists.
        val fielding = fielded.field(squad.team.id!!, squad.game, season.id!!)
        return entries.save(
            TeamRosterEntry(
                teamSeason = fielding,
                handle = handle,
                teamRole = TeamRole.PLAYER,
                userId = userId,
                displayName = "Recorded Name",
            ),
        )
    }

    /** Says whether the esports pages may print [user]'s name, the way the Games page does. */
    private fun consent(
        user: User,
        shown: Boolean,
    ) {
        mvc
            .perform(
                put("/users/{userId}/name-on-rosters", user.id)
                    .contentType(MediaType.APPLICATION_JSON)
                    .content("""{"shown":$shown}""")
                    .with(signedIn(user)),
            ).andExpect(status().isOk)
            .andExpect(jsonPath("$.nameOnRosters").value(shown))
    }

    @Test
    fun `a member who allows it is named beside their handle`() {
        val member = createUserWithRole(Role.MEMBER)
        consent(member, shown = true)
        val playing = season()
        val squad = team("VALORANT")
        seat(squad, playing, "theirHandle", member.id)

        mvc
            .perform(get("/esports/games/{game}", "VALORANT").param("seasonId", playing.id.toString()))
            .andExpect(status().isOk)
            .andExpect(jsonPath("$.teams[?(@.name == '${squad.name}')].members[0].handle").value("theirHandle"))
            .andExpect(jsonPath("$.teams[?(@.name == '${squad.name}')].members[0].name").value(member.fullName))
    }

    @Test
    fun `a member who has not allowed it is shown by handle alone`() {
        val member = createUserWithRole(Role.MEMBER)
        consent(member, shown = false)
        val playing = season()
        val squad = team("CS2")
        seat(squad, playing, "quietHandle", member.id)

        mvc
            .perform(get("/esports/games/{game}", "CS2").param("seasonId", playing.id.toString()))
            .andExpect(status().isOk)
            .andExpect(jsonPath("$.teams[?(@.name == '${squad.name}')].members[0].handle").value("quietHandle"))
            .andExpect(jsonPath("$.teams[?(@.name == '${squad.name}')].members[0].name").doesNotExist())
    }

    @Test
    fun `somebody who never said so has consented to nothing`() {
        val member = createUserWithRole(Role.MEMBER)
        val playing = season()
        val squad = team("LEAGUE_OF_LEGENDS")
        seat(squad, playing, "noProfile", member.id)

        mvc
            .perform(get("/esports/games/{game}", "LEAGUE_OF_LEGENDS").param("seasonId", playing.id.toString()))
            .andExpect(status().isOk)
            .andExpect(jsonPath("$.teams[?(@.name == '${squad.name}')].members[0].name").doesNotExist())
    }

    @Test
    fun `somebody without a membership may be named too`() {
        val guest = createUserWithRole(Role.GUEST)
        consent(guest, shown = true)
        val playing = season()
        val squad = team("GEOGUESSR")
        seat(squad, playing, "guestHandle", guest.id)

        mvc
            .perform(get("/esports/games/{game}", "GEOGUESSR").param("seasonId", playing.id.toString()))
            .andExpect(jsonPath("$.teams[?(@.name == '${squad.name}')].members[0].name").value(guest.fullName))
    }

    @Test
    fun `an entry nobody is linked to is never named, whatever it was recorded with`() {
        val playing = season()
        val squad = team("ROCKET_LEAGUE")
        seat(squad, playing, "unattributed", userId = null)

        mvc
            .perform(get("/esports/games/{game}", "ROCKET_LEAGUE").param("seasonId", playing.id.toString()))
            .andExpect(status().isOk)
            .andExpect(jsonPath("$.teams[?(@.name == '${squad.name}')].members[0].handle").value("unattributed"))
            // The seat carries "Recorded Name"; nobody is linked to it, so nobody is named.
            // Scoped to the member: a season and a team have a `name` of their own.
            .andExpect(jsonPath("$.teams[?(@.name == '${squad.name}')].members[0].name").doesNotExist())
    }

    @Test
    fun `revoking it takes the name out of the roster on the next read`() {
        val member = createUserWithRole(Role.MEMBER)
        consent(member, shown = true)
        val playing = season()
        val squad = team("TRACKMANIA")
        seat(squad, playing, "revoker", member.id)

        mvc
            .perform(get("/esports/games/{game}", "TRACKMANIA").param("seasonId", playing.id.toString()))
            .andExpect(jsonPath("$.teams[?(@.name == '${squad.name}')].members[0].name").value(member.fullName))

        consent(member, shown = false)

        mvc
            .perform(get("/esports/games/{game}", "TRACKMANIA").param("seasonId", playing.id.toString()))
            .andExpect(status().isOk)
            .andExpect(jsonPath("$.teams[?(@.name == '${squad.name}')].members[0].name").doesNotExist())
    }
}

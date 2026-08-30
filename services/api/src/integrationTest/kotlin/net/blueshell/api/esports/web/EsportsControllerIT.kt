package net.blueshell.api.esports.web

import net.blueshell.api.esports.domain.TeamSeasonService
import net.blueshell.api.esports.persistence.Season
import net.blueshell.api.esports.persistence.Team
import net.blueshell.api.esports.persistence.TeamRosterEntry
import net.blueshell.api.esports.persistence.UserGameAccount
import net.blueshell.api.esports.persistence.SeasonRepository
import net.blueshell.api.esports.persistence.TeamRepository
import net.blueshell.api.esports.persistence.TeamRosterEntryRepository
import net.blueshell.api.esports.persistence.UserGameAccountRepository
import net.blueshell.api.shared.enums.Role
import net.blueshell.api.shared.enums.TeamRole
import net.blueshell.api.testsupport.UserTestSupport
import org.junit.jupiter.api.Nested
import org.junit.jupiter.api.Test
import org.springframework.beans.factory.annotation.Autowired
import org.springframework.boot.test.context.SpringBootTest
import org.springframework.http.MediaType
import org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get
import org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post
import org.springframework.test.web.servlet.request.MockMvcRequestBuilders.put
import org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath
import org.springframework.test.web.servlet.result.MockMvcResultMatchers.status
import java.time.LocalDate

@SpringBootTest
class EsportsControllerIT : UserTestSupport() {
    @Autowired
    private lateinit var seasons: SeasonRepository

    @Autowired
    private lateinit var teams: TeamRepository

    @Autowired
    private lateinit var entries: TeamRosterEntryRepository

    @Autowired
    private lateinit var accounts: UserGameAccountRepository

    @Autowired
    private lateinit var fielded: TeamSeasonService

    private fun season(name: String, from: LocalDate, to: LocalDate): Season =
        seasons.save(Season(name = name, startDate = from, endDate = to))

    private fun team(game: String, name: String): Team =
        teams.save(Team(game = game, name = name))

    private fun entry(
        team: Team,
        season: Season,
        handle: String,
        role: TeamRole = TeamRole.PLAYER,
        userId: Long? = null,
        displayName: String? = null,
    ): TeamRosterEntry {
        // Naming somebody to a team says it is fielded that season, which is what the page
        // reads; a row written straight to the repository has to say so itself.
        fielded.field(team.id!!, season.id!!)
        return entries.save(
            TeamRosterEntry(
                team = team,
                season = season,
                handle = handle,
                teamRole = role,
                userId = userId,
                displayName = displayName,
            ),
        )
    }

    @Nested
    inner class PublicPage {
        @Test
        fun `shows the newest season's roster when none is asked for`() {
            val older = season("Older ${System.nanoTime()}", LocalDate.of(2021, 9, 1), LocalDate.of(2022, 1, 31))
            val newer = season("Newer ${System.nanoTime()}", LocalDate.of(2024, 9, 1), LocalDate.of(2025, 1, 31))
            val squad = team("TRACKMANIA", "Squad ${System.nanoTime()}")
            entry(squad, older, "backThen")
            entry(squad, newer, "rightNow")

            mvc.perform(get("/esports/games/{game}", "TRACKMANIA"))
                .andExpect(status().isOk)
                .andExpect(jsonPath("$.season.name").value(newer.name))
                .andExpect(jsonPath("$.teams[?(@.name == '${squad.name}')].members[0].handle").value("rightNow"))
        }

        @Test
        fun `shows an older season when asked for it`() {
            val older = season("Older ${System.nanoTime()}", LocalDate.of(2021, 9, 1), LocalDate.of(2022, 1, 31))
            val newer = season("Newer ${System.nanoTime()}", LocalDate.of(2024, 9, 1), LocalDate.of(2025, 1, 31))
            val squad = team("GEOGUESSR", "Squad ${System.nanoTime()}")
            entry(squad, older, "backThen")
            entry(squad, newer, "rightNow")

            mvc.perform(get("/esports/games/{game}", "GEOGUESSR").param("seasonId", older.id.toString()))
                .andExpect(status().isOk)
                .andExpect(jsonPath("$.season.id").value(older.id))
                .andExpect(jsonPath("$.teams[?(@.name == '${squad.name}')].members[0].handle").value("backThen"))
        }

        @Test
        fun `never publishes a real name, however the entry was recovered`() {
            val playing = season("Named ${System.nanoTime()}", LocalDate.of(2023, 9, 1), LocalDate.of(2024, 1, 31))
            val squad = team("SMASH", "Squad ${System.nanoTime()}")
            entry(squad, playing, "handleOnly", displayName = "Ada Lovelace")

            mvc.perform(get("/esports/games/{game}", "SMASH"))
                .andExpect(status().isOk)
                .andExpect(jsonPath("$..members[0].handle").value("handleOnly"))
                .andExpect(jsonPath("$..displayName").doesNotExist())
                .andExpect(jsonPath("$..userId").doesNotExist())
        }

        @Test
        fun `renders a linked member by the handle they hold now, not the one they played under`() {
            val member = createUserWithRole(Role.MEMBER)
            accounts.save(UserGameAccount(userId = member.id!!, game = "CS2", handle = "renamed"))
            val playing = season("Linked ${System.nanoTime()}", LocalDate.of(2023, 9, 1), LocalDate.of(2024, 1, 31))
            val squad = team("CS2", "Squad ${System.nanoTime()}")
            entry(squad, playing, "playedAs", userId = member.id)

            mvc.perform(get("/esports/games/{game}", "CS2").param("seasonId", playing.id.toString()))
                .andExpect(status().isOk)
                .andExpect(jsonPath("$.teams[?(@.name == '${squad.name}')].members[0].handle").value("renamed"))
        }
    }

    @Nested
    inner class Writing {
        @Test
        fun `a member may not add a team`() {
            val member = createUserWithRole(Role.MEMBER)

            mvc.perform(
                post("/esports/teams")
                    .with(bearer(member))
                    .contentType(MediaType.APPLICATION_JSON)
                    .content("""{"game":"VALORANT","name":"Nope"}"""),
            ).andExpect(status().isForbidden)
        }

        @Test
        fun `the board adds a team and puts somebody on its roster`() {
            val board = createUserWithRole(Role.BOARD)
            val playing = season("Board ${System.nanoTime()}", LocalDate.of(2025, 9, 1), LocalDate.of(2026, 1, 31))

            val created = mvc.perform(
                post("/esports/teams")
                    .with(bearer(board))
                    .contentType(MediaType.APPLICATION_JSON)
                    .content("""{"game":"VALORANT","name":"BS Fresh ${System.nanoTime()}"}"""),
            )
                .andExpect(status().isCreated)
                .andReturn()
                .response
                .contentAsString

            val teamId = Regex("\"id\":(\\d+)").find(created)!!.groupValues[1]

            mvc.perform(
                post("/esports/teams/{teamId}/roster", teamId)
                    .with(bearer(board))
                    .contentType(MediaType.APPLICATION_JSON)
                    .content("""{"seasonId":${playing.id},"handle":"newcomer","role":"PLAYER"}"""),
            )
                .andExpect(status().isCreated)
                .andExpect(jsonPath("$.handle").value("newcomer"))
                .andExpect(jsonPath("$.userId").doesNotExist())
        }

        @Test
        fun `an entry can be attached to a member and detached again`() {
            val board = createUserWithRole(Role.BOARD)
            val member = createUserWithRole(Role.MEMBER)
            val playing = season("Link ${System.nanoTime()}", LocalDate.of(2025, 9, 1), LocalDate.of(2026, 1, 31))
            val squad = team("ROCKET_LEAGUE", "Squad ${System.nanoTime()}")
            val row = entry(squad, playing, "someone")

            mvc.perform(
                put("/esports/roster/{id}/member", row.id)
                    .with(bearer(board))
                    .contentType(MediaType.APPLICATION_JSON)
                    .content("""{"userId":${member.id}}"""),
            )
                .andExpect(status().isOk)
                .andExpect(jsonPath("$.userId").value(member.id))

            mvc.perform(
                put("/esports/roster/{id}/member", row.id)
                    .with(bearer(board))
                    .contentType(MediaType.APPLICATION_JSON)
                    .content("""{}"""),
            )
                .andExpect(status().isOk)
                .andExpect(jsonPath("$.userId").doesNotExist())
        }
    }

    @Nested
    inner class GameAccounts {
        @Test
        fun `a member sets and clears their own handle`() {
            val member = createUserWithRole(Role.MEMBER)

            mvc.perform(
                put("/users/{userId}/game-accounts/{game}", member.id, "VALORANT")
                    .with(bearer(member))
                    .contentType(MediaType.APPLICATION_JSON)
                    .content("""{"handle":"mine"}"""),
            )
                .andExpect(status().isOk)
                .andExpect(jsonPath("$.handle").value("mine"))

            mvc.perform(
                put("/users/{userId}/game-accounts/{game}", member.id, "VALORANT")
                    .with(bearer(member))
                    .contentType(MediaType.APPLICATION_JSON)
                    .content("""{"handle":"mine again"}"""),
            ).andExpect(status().isOk)

            mvc.perform(get("/users/{userId}/game-accounts", member.id).with(bearer(member)))
                .andExpect(status().isOk)
                // Setting it twice replaces rather than accumulates: one handle per game.
                .andExpect(jsonPath("$.length()").value(1))
                .andExpect(jsonPath("$[0].handle").value("mine again"))
        }

        @Test
        fun `a member may not set somebody else's handle`() {
            val member = createUserWithRole(Role.MEMBER)
            val other = createUserWithRole(Role.MEMBER)

            mvc.perform(
                put("/users/{userId}/game-accounts/{game}", other.id, "VALORANT")
                    .with(bearer(member))
                    .contentType(MediaType.APPLICATION_JSON)
                    .content("""{"handle":"not mine"}"""),
            ).andExpect(status().isForbidden)
        }
    }
}

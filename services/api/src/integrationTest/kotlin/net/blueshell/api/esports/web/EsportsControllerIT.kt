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
import net.blueshell.api.shared.enums.FileType
import net.blueshell.api.shared.enums.Role
import net.blueshell.api.shared.enums.TeamRole
import net.blueshell.api.testsupport.UserTestSupport
import net.blueshell.api.user.persistence.User
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

    /**
     * A team and the game it is fielded in. The fielding names the game, not the team, so a
     * fixture meaning "this team, playing this game" carries both.
     */
    private data class Squad(val team: Team, val game: String) {
        val name: String get() = team.name
    }

    private fun team(game: String, name: String): Squad =
        Squad(teams.save(Team(name = name)), game)

    private fun entry(
        squad: Squad,
        season: Season,
        handle: String,
        role: TeamRole = TeamRole.PLAYER,
        userId: Long? = null,
        displayName: String? = null,
    ): TeamRosterEntry {
        // A line-up hangs off the fielding, so the fielding is what the entry is written
        // against — there is nothing to attach one to until it exists.
        val fielding = fielded.field(squad.team.id!!, squad.game, season.id!!)
        return entries.save(
            TeamRosterEntry(
                teamSeason = fielding,
                handle = handle,
                teamRole = role,
                userId = userId,
                displayName = displayName,
            ),
        )
    }

    @Nested
    inner class PublicRead {
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


    /**
     * A team's own art, which until now was proven only in a browser.
     *
     * Both of a team's pictures are staged by a picker and committed by the save that names
     * them, so what these ask is that a path a caller hands in comes back on the team, that a
     * save naming none takes them away, and that the icon reaches the read the game answers.
     */
    @Test
    fun `a team made for one game is fielded in another it already plays`() {
        val board = createUserWithRole(Role.BOARD)
        val unique = System.nanoTime()
        val playing = season("Both $unique", LocalDate.of(2033, 9, 1), LocalDate.of(2034, 1, 31))

        // Created naming no game: a team is the association's, and the game arrives when it is
        // fielded. This is the whole of what the shared pool buys the board.
        val created = mvc.perform(
            post("/esports/teams").with(bearer(board))
                .contentType(MediaType.APPLICATION_JSON)
                .content("""{"name":"BS Both Ways $unique"}"""),
        )
            .andExpect(status().isCreated)
            .andReturn().response.contentAsString
        val teamId = Regex("\"id\":(\\d+)").find(created)!!.groupValues[1]

        listOf("VALORANT", "TRACKMANIA").forEach { game ->
            mvc.perform(
                put("/esports/seasons/{seasonId}/teams/{teamId}", playing.id, teamId)
                    .with(bearer(board))
                    .contentType(MediaType.APPLICATION_JSON)
                    .content("""{"game":"$game"}"""),
            )
                .andExpect(status().isOk)
                .andExpect(jsonPath("$.game").value(game))
        }

        // One team, two games, one season — and a line-up of its own in each, which is what
        // the pool being shared and the roster not amounts to.
        //
        // Asked as the board, which is who asks it: the route carries `@PermitAll`, but the
        // security config has never whitelisted it, so an anonymous caller is refused. Nothing
        // public reads it — a game's own read answers it — and squaring that declaration with
        // the configuration is a change to the security surface rather than to this feature.
        mvc.perform(get("/esports/teams/{teamId}/seasons", teamId).with(bearer(board)))
            .andExpect(status().isOk)
            .andExpect(jsonPath("$[?(@.game == 'VALORANT')]").exists())
            .andExpect(jsonPath("$[?(@.game == 'TRACKMANIA')]").exists())
    }

    @Nested
    inner class TeamArt {
        @Test
        fun `a team is added with an icon, and drawn with the art of the season it is fielded in`() {
            val board = createUserWithRole(Role.BOARD)
            val banner = storedPicture(board, FileType.TEAM_BANNER)
            val icon = storedPicture(board, FileType.TEAM_ICON)
            val playing = season("Drawn ${System.nanoTime()}", LocalDate.of(2025, 9, 1), LocalDate.of(2026, 1, 31))

            // The logo is the team's: it is who they are, wherever they play.
            val created = mvc.perform(
                post("/esports/teams")
                    .with(bearer(board))
                    .contentType(MediaType.APPLICATION_JSON)
                    .content("""{"name":"BS Drawn ${System.nanoTime()}","icon":"$icon"}"""),
            )
                .andExpect(status().isCreated)
                .andExpect(jsonPath("$.icon.path").value(icon))
                .andReturn().response.contentAsString
            val teamId = Regex("\"id\":(\\d+)").find(created)!!.groupValues[1]

            // The banner is the fielding's, because the art is game-flavoured and this team
            // may be drawn differently in a game it also plays.
            mvc.perform(
                put("/esports/seasons/{seasonId}/teams/{teamId}", playing.id, teamId)
                    .with(bearer(board))
                    .contentType(MediaType.APPLICATION_JSON)
                    .content("""{"game":"VALORANT","banner":"$banner"}"""),
            )
                .andExpect(status().isOk)
                .andExpect(jsonPath("$.banner.path").value(banner))
        }

        /**
         * The payload names the narrower copies, which is how a caller asks for one of them.
         *
         * Nothing asserted this before, and its failure is invisible: a payload that carried
         * only the full-size picture would draw correctly and at several times the weight, on
         * every screen, for ever. A caller composes a `srcset` out of exactly this list, and an
         * empty one means it falls back to the master.
         */
        @Test
        fun `the payload carries the widths a banner is stored at`() {
            val board = createUserWithRole(Role.BOARD)
            // Wide enough to have copies at all: the ladder for a banner starts at 320 and
            // nothing is upscaled.
            val banner = storedPicture(board, FileType.TEAM_BANNER, width = 700, height = 394)
            val playing = season("Widths ${System.nanoTime()}", LocalDate.of(2025, 9, 1), LocalDate.of(2026, 1, 31))

            val created = mvc.perform(
                post("/esports/teams")
                    .with(bearer(board))
                    .contentType(MediaType.APPLICATION_JSON)
                    .content("""{"name":"BS Widths ${System.nanoTime()}"}"""),
            )
                .andExpect(status().isCreated)
                .andReturn().response.contentAsString
            val teamId = Regex("\"id\":(\\d+)").find(created)!!.groupValues[1]

            mvc.perform(
                put("/esports/seasons/{seasonId}/teams/{teamId}", playing.id, teamId)
                    .with(bearer(board))
                    .contentType(MediaType.APPLICATION_JSON)
                    .content("""{"game":"VALORANT","banner":"$banner"}"""),
            )
                .andExpect(status().isOk)
                // 960 and up are wider than the picture, so they do not exist to be named.
                .andExpect(jsonPath("$.banner.renditions.length()").value(2))
                .andExpect(jsonPath("$.banner.renditions[0].width").value(320))
                .andExpect(jsonPath("$.banner.renditions[1].width").value(640))
                .andExpect(jsonPath("$.banner.renditions[0].url").exists())
        }

        @Test
        fun `a save that names another picture replaces the team's logo`() {
            val board = createUserWithRole(Role.BOARD)
            val team = teams.save(Team(name = "BS Redrawn ${System.nanoTime()}"))
            val icon = storedPicture(board, FileType.TEAM_ICON)

            mvc.perform(
                put("/esports/teams/{id}", team.id)
                    .with(bearer(board))
                    .contentType(MediaType.APPLICATION_JSON)
                    .content("""{"name":"${team.name}","icon":"$icon"}"""),
            )
                .andExpect(status().isOk)
                .andExpect(jsonPath("$.icon.path").value(icon))
        }

        @Test
        fun `a save that names no picture takes the team's logo away`() {
            val board = createUserWithRole(Role.BOARD)
            val team = teams.save(Team(name = "BS Undrawn ${System.nanoTime()}"))
            val icon = storedPicture(board, FileType.TEAM_ICON)
            mvc.perform(
                put("/esports/teams/{id}", team.id)
                    .with(bearer(board))
                    .contentType(MediaType.APPLICATION_JSON)
                    .content("""{"name":"${team.name}","icon":"$icon"}"""),
            ).andExpect(status().isOk)

            mvc.perform(
                put("/esports/teams/{id}", team.id)
                    .with(bearer(board))
                    .contentType(MediaType.APPLICATION_JSON)
                    .content("""{"name":"${team.name}"}"""),
            )
                .andExpect(status().isOk)
                .andExpect(jsonPath("$.icon").doesNotExist())
        }

        @Test
        fun `a team's icon reaches the read its game answers`() {
            val board = createUserWithRole(Role.BOARD)
            val icon = storedPicture(board, FileType.TEAM_ICON)
            val playing = season("Drawn ${System.nanoTime()}", LocalDate.of(2025, 9, 1), LocalDate.of(2026, 1, 31))
            val team = teams.save(Team(name = "BS Shown ${System.nanoTime()}"))
            fielded.field(team.id!!, "VALORANT", playing.id!!)
            mvc.perform(
                put("/esports/teams/{id}", team.id)
                    .with(bearer(board))
                    .contentType(MediaType.APPLICATION_JSON)
                    .content("""{"name":"${team.name}","icon":"$icon"}"""),
            ).andExpect(status().isOk)

            mvc.perform(get("/esports/games/{game}", "VALORANT").param("seasonId", playing.id.toString()))
                .andExpect(status().isOk)
                .andExpect(jsonPath("$.teams[?(@.name == '${team.name}')].icon.path").value(icon))
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
                    .content("""{"game":"VALORANT","seasonId":${playing.id},"handle":"newcomer","role":"PLAYER"}"""),
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

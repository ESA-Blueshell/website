package net.blueshell.api.esports.persistence

import net.blueshell.api.esports.domain.TeamSeasonService
import net.blueshell.api.shared.enums.Role
import net.blueshell.api.user.persistence.User
import net.blueshell.api.testsupport.UserTestSupport
import org.assertj.core.api.Assertions.assertThatThrownBy
import org.junit.jupiter.api.Test
import org.springframework.beans.factory.annotation.Autowired
import org.springframework.boot.test.context.SpringBootTest
import org.springframework.http.MediaType
import org.springframework.test.web.servlet.request.MockMvcRequestBuilders.delete
import net.blueshell.api.shared.enums.FileType
import org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get
import org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post
import org.springframework.test.web.servlet.request.MockMvcRequestBuilders.put
import org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath
import org.springframework.test.web.servlet.result.MockMvcResultMatchers.status
import java.time.LocalDate

/**
 * A game's own record: what it is called, the art it is drawn with, the address it answers to,
 * what is said about it, where it sits, and whether the association still fields a team in it.
 * All of it lived in the frontend, where a change to any of it was a deploy.
 */
@SpringBootTest
class GameIT : UserTestSupport() {
    @Autowired private lateinit var teams: TeamRepository

    @Autowired private lateinit var seasons: SeasonRepository

    @Autowired private lateinit var fielded: TeamSeasonService

    /**
     * A team fielded in a game, which is what makes a game hold something. A team on its own
     * holds nothing now: it belongs to the association rather than to a game, and the fielding
     * is what says which game was played.
     */
    private fun fieldATeamIn(game: String): Long {
        val unique = System.nanoTime()
        val team = teams.save(Team(name = "BS Holders $unique"))
        val season = seasons.save(
            Season(
                name = "Season $unique",
                startDate = LocalDate.of(2030, 9, 1),
                endDate = LocalDate.of(2031, 1, 31),
            ),
        )
        fielded.field(team.id!!, game, season.id!!)
        return team.id!!
    }

    /**
     * Nothing is seeded here. The games are what the migration established, and the suite's
     * clean-up restores them after every case rather than wiping them, because a team and a
     * game account point at one.
     *
     * Which games those are is not asserted here — a game added to `games.csv` has nothing to
     * say about foreign keys or permissions, and used to fail this suite anyway (#1068).
     * `ShippedGamesRealSeedTest` reads the file itself.
     */
    @Test
    fun `the listing is ordered by where each game is placed, not by when it was added`() {
        val board = createUserWithRole(Role.BOARD)
        val name = aGameCalled(board, "Aardvark")

        // Placed first, so it leads a listing it was added to last.
        mvc.perform(
            put("/esports/games/{game}", name.uppercase())
                .with(bearer(board))
                .contentType(MediaType.APPLICATION_JSON)
                .content("""{"name":"$name","slug":"${name.lowercase()}","sortIndex":0,"fielded":false}"""),
        ).andExpect(status().isOk)

        mvc.perform(get("/esports/games"))
            .andExpect(status().isOk)
            .andExpect(jsonPath("$[0].code").value(name.uppercase()))
    }

    @Test
    fun `a code naming no game is refused with a reason`() {
        // Nothing makes such a code unrepresentable any more, so the edge has to say so.
        mvc.perform(get("/esports/games/{game}", "PONG"))
            .andExpect(status().isBadRequest)
    }

    @Test
    fun `a team cannot be fielded in a game that does not exist`() {
        val board = createUserWithRole(Role.BOARD)
        val unique = System.nanoTime()
        val team = teams.save(Team(name = "Table Tennis Firsts $unique"))
        val season = seasons.save(
            Season(
                name = "Season $unique",
                startDate = LocalDate.of(2030, 9, 1),
                endDate = LocalDate.of(2031, 1, 31),
            ),
        )

        // The team itself names no game — it is the association's — so the refusal belongs to
        // the fielding, which is where a game is named.
        mvc.perform(
            put("/esports/seasons/{seasonId}/teams/{teamId}", season.id, team.id)
                .with(bearer(board))
                .contentType(MediaType.APPLICATION_JSON)
                .content("""{"game":"PONG"}"""),
        )
            .andExpect(status().isBadRequest)
    }

    /** A game this suite made, so what it is called is this suite's business and not the seed's. */
    private fun aGameCalled(board: User, stem: String): String {
        val name = "$stem${System.nanoTime()}"
        mvc.perform(
            post("/esports/games")
                .with(bearer(board))
                .contentType(MediaType.APPLICATION_JSON)
                .content("""{"name":"$name","slug":"${name.lowercase()}"}"""),
        ).andExpect(status().isCreated)
        return name
    }

    @Test
    fun `a game nobody has played is not current, and is still answered for`() {
        // Derived rather than declared, and this suite starts from a database with no seasons
        // and no fieldings in it: nothing has been played, so nothing is current, and every
        // game is still answered for. What the rule answers once games have been played is
        // asserted in CurrentlyPlayedIT, which builds the seasons it needs.
        mvc.perform(get("/esports/games"))
            .andExpect(jsonPath("$[?(@.code == 'CSGO')].current").value(false))
            .andExpect(jsonPath("$[?(@.code == 'VALORANT')].current").value(false))
            .andExpect(jsonPath("$[?(@.code == 'VALORANT')].name").value("Valorant"))
    }

    /**
     * The pictures are file references, which the suite's clean-up blanks rather than restores,
     * so what a game carries here is its colour. That the shipped art reaches a game is asserted
     * where the step that puts it there is, in `ShippedArtIT`.
     */
    @Test
    fun `a game carries the colour it is drawn with`() {
        val board = createUserWithRole(Role.BOARD)
        val name = aGameCalled(board, "Pinball")
        val code = name.uppercase()

        mvc.perform(
            put("/esports/games/{game}", code)
                .with(bearer(board))
                .contentType(MediaType.APPLICATION_JSON)
                .content("""{"name":"$name","slug":"${name.lowercase()}","accent":"#ff4655","sortIndex":9,"fielded":true}"""),
        ).andExpect(status().isOk)

        mvc.perform(get("/esports/games"))
            .andExpect(jsonPath("$[?(@.code == '$code')].accent").value("#ff4655"))
    }

    @Test
    fun `a game nobody has drawn art for says so rather than inventing any`() {
        // The island reads such a game on the association's own colour; it does not go missing.
        val board = createUserWithRole(Role.BOARD)
        val name = aGameCalled(board, "Pinball")
        val code = name.uppercase()

        mvc.perform(get("/esports/games"))
            .andExpect(jsonPath("$[?(@.code == '$code')].name").value(name))
            .andExpect(jsonPath("$[?(@.code == '$code')].accent").doesNotExist())
            .andExpect(jsonPath("$[?(@.code == '$code')].icon").doesNotExist())
            .andExpect(jsonPath("$[?(@.code == '$code')].banner").doesNotExist())
    }

    /**
     * The enum made an unknown code unrepresentable in Kotlin, and the database was told
     * nothing. Once a game is a row rather than a compiled constant that stops being true, so
     * the tie is stated where the rows live. Written natively because a well-typed caller
     * cannot express the code these reject; each states the accepted case alongside the
     * rejected one, so a statement that is simply malformed cannot read as the tie holding.
     */
    @Test
    fun `a fielding cannot name a game that does not exist`() {
        // The tie is on the fielding rather than on the team: a team is the association's and
        // plays whatever games it plays, so the game it played is a fact about being fielded.
        insertFielding("VALORANT")

        assertThatThrownBy { insertFielding("PONG") }
            .hasMessageContaining("fk_team_season_game")
    }

    @Test
    fun `a member's game account cannot name a game that does not exist`() {
        val member = createUserWithRole(Role.MEMBER)
        insertAccountFor(member.id!!, "VALORANT")

        assertThatThrownBy { insertAccountFor(member.id!!, "PONG") }
            .hasMessageContaining("fk_user_game_account_game")
    }

    private fun insertFielding(game: String) = transactionTemplate.execute {
        val unique = System.nanoTime()
        entityManager.createNativeQuery("INSERT INTO team (name) VALUES (:name)")
            .setParameter("name", "Blueshell Firsts $unique")
            .executeUpdate()
        entityManager.createNativeQuery(
            "INSERT INTO season (name, start_date, end_date) VALUES (:name, '2030-09-01', '2031-01-31')",
        ).setParameter("name", "Season $unique").executeUpdate()
        entityManager.createNativeQuery(
            """
            INSERT INTO team_season (team_id, game, season_id)
            SELECT t.id, :game, s.id FROM team t, season s
            WHERE t.name = :teamName AND s.name = :seasonName
            """,
        )
            .setParameter("game", game)
            .setParameter("teamName", "Blueshell Firsts $unique")
            .setParameter("seasonName", "Season $unique")
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
                .content("""{"name":"GeoGuessr","slug":"geoguessr","intro":"Guessing, competitively.","sortIndex":5,"fielded":true}"""),
        )
            .andExpect(status().isOk)
            .andExpect(jsonPath("$.intro").value("Guessing, competitively."))

        mvc.perform(get("/esports/games"))
            .andExpect(jsonPath("$[?(@.code == 'GEOGUESSR')].intro").value("Guessing, competitively."))
    }

    @Test
    fun `two games cannot answer to the same address`() {
        val board = createUserWithRole(Role.BOARD)

        mvc.perform(
            put("/esports/games/{game}", "SMASH")
                .with(bearer(board))
                .contentType(MediaType.APPLICATION_JSON)
                .content("""{"name":"Super Smash Bros.","slug":"valorant","intro":null,"sortIndex":8,"fielded":false}"""),
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
                .content("""{"name":"Trackmania","slug":"trackmania","intro":"Driving, fast.","sortIndex":6,"fielded":true}"""),
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
            .andExpect(jsonPath("$.code").value("ROCKET_LEAGUE_2"))
            // Nobody has drawn it anything, so it reads on the island's own colour.
            .andExpect(jsonPath("$.accent").doesNotExist())
            // Nobody has played it, so it is not among the games the association currently
            // plays. It becomes one by having a team fielded in it, not by being created.
            .andExpect(jsonPath("$.current").value(false))

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
        val unique = System.nanoTime()
        val team = teams.save(Team(name = "BS Paddlers $unique"))
        val season = seasons.save(
            Season(
                name = "Season $unique",
                startDate = LocalDate.of(2030, 9, 1),
                endDate = LocalDate.of(2031, 1, 31),
            ),
        )

        mvc.perform(
            put("/esports/seasons/{seasonId}/teams/{teamId}", season.id, team.id).with(bearer(board))
                .contentType(MediaType.APPLICATION_JSON)
                .content("""{"game":"PONG"}"""),
        )
            .andExpect(status().isOk)
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
            .andExpect(jsonPath("$.code").value("AddressTaken"))
            .andExpect(jsonPath("$.gameName").value("Valorant"))
            .andExpect(jsonPath("$.address").value("valorant"))
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
            .andExpect(jsonPath("$.code").value("AGE_OF_EMPIRES_II"))
    }

    @Test
    fun `a game cannot claim the index's own address`() {
        val board = createUserWithRole(Role.BOARD)

        // It would have a record nothing could reach, because that address is taken.
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
    fun `the board corrects a game's name, colour and art where it is shown`() {
        val board = createUserWithRole(Role.BOARD)
        // Both pictures name something already in storage: choosing one stores it, and the save
        // is what puts it on the game.
        val banner = storedPicture(board, FileType.GAME_BANNER)
        val icon = storedPicture(board, FileType.GAME_ICON)

        mvc.perform(
            put("/esports/games/{game}", "TRACKMANIA")
                .with(bearer(board))
                .contentType(MediaType.APPLICATION_JSON)
                .content(
                    """
                    {"name":"TrackMania","slug":"trackmania","intro":"Driving, fast.",
                     "accent":"#22d3ee","banner":"$banner","icon":"$icon",
                     "sortIndex":6,"fielded":true}
                    """.trimIndent(),
                ),
        )
            .andExpect(status().isOk)
            .andExpect(jsonPath("$.name").value("TrackMania"))
            .andExpect(jsonPath("$.accent").value("#22d3ee"))
            .andExpect(jsonPath("$.banner.path").value(banner))
            .andExpect(jsonPath("$.icon.path").value(icon))
            // The code is the identity everything else points at, and is not the request's to set.
            .andExpect(jsonPath("$.code").value("TRACKMANIA"))
    }

    /**
     * A game's icon is an upload rather than a name, so a path naming nothing in storage is
     * refused rather than accepted and silently drawn as nothing.
     */
    @Test
    fun `an icon naming nothing in storage is refused rather than quietly drawn as nothing`() {
        val board = createUserWithRole(Role.BOARD)

        mvc.perform(
            put("/esports/games/{game}", "TRACKMANIA")
                .with(bearer(board))
                .contentType(MediaType.APPLICATION_JSON)
                .content(
                    """
                    {"name":"Trackmania","slug":"trackmania","accent":"#22d3ee",
                     "icon":"game-icons/nothing-is-stored-here.webp","sortIndex":6,"fielded":true}
                    """.trimIndent(),
                ),
        )
            .andExpect(status().isBadRequest)
    }

    @Test
    fun `art can be taken away again, leaving the island's own colour`() {
        val board = createUserWithRole(Role.BOARD)

        mvc.perform(
            put("/esports/games/{game}", "VALORANT")
                .with(bearer(board))
                .contentType(MediaType.APPLICATION_JSON)
                .content(
                    """{"name":"Valorant","slug":"valorant","accent":"","banner":"","icon":"","sortIndex":1,"fielded":true}""",
                ),
        )
            .andExpect(status().isOk)
            .andExpect(jsonPath("$.accent").doesNotExist())
            .andExpect(jsonPath("$.banner").doesNotExist())
            .andExpect(jsonPath("$.icon").doesNotExist())
    }

    @Test
    fun `a game the association stops entering keeps everything it holds`() {
        // There is no act that retires a game any more. It stops being current by not being
        // entered, and everything it ever played stays exactly where it was.
        mvc.perform(get("/esports/games"))
            .andExpect(jsonPath("$[?(@.code == 'CSGO')].current").value(false))
            .andExpect(jsonPath("$[?(@.code == 'CSGO')].name").value("CS:GO"))
            .andExpect(jsonPath("$[?(@.code == 'CSGO')].slug").value("counter-strike-global-offensive"))

        // Its teams are still there to read, which is the whole reason it is kept.
        mvc.perform(get("/esports/games/{game}", "CSGO"))
            .andExpect(status().isOk)
    }

    @Test
    fun `a game cannot be renamed to nothing`() {
        val board = createUserWithRole(Role.BOARD)

        mvc.perform(
            put("/esports/games/{game}", "VALORANT")
                .with(bearer(board))
                .contentType(MediaType.APPLICATION_JSON)
                .content("""{"name":"   ","slug":"valorant","sortIndex":1,"fielded":true}"""),
        )
            .andExpect(status().isBadRequest)
    }

    @Test
    fun `what a game holds is read before the question is put`() {
        val board = createUserWithRole(Role.BOARD)

        mvc.perform(get("/esports/games/{game}/contents", "VALORANT").with(bearer(board)))
            .andExpect(status().isOk)
            .andExpect(jsonPath("$.teams").isNumber)
            .andExpect(jsonPath("$.players").isNumber)
    }

    @Test
    fun `a game holding nothing is removed, and its address stops answering`() {
        val board = createUserWithRole(Role.BOARD)
        mvc.perform(
            post("/esports/games").with(bearer(board))
                .contentType(MediaType.APPLICATION_JSON)
                .content("""{"name":"Pong","slug":"pong"}"""),
        ).andExpect(status().isCreated)

        mvc.perform(delete("/esports/games/{game}", "PONG").with(bearer(board)))
            .andExpect(status().isNoContent)

        mvc.perform(get("/esports/games/{game}", "PONG")).andExpect(status().isBadRequest)
        mvc.perform(get("/esports/games"))
            .andExpect(jsonPath("$[?(@.code == 'PONG')]").doesNotExist())
    }

    @Test
    fun `a game with teams recorded in it is refused, and its history stays`() {
        val board = createUserWithRole(Role.BOARD)
        fieldATeamIn("VALORANT")

        mvc.perform(delete("/esports/games/{game}", "VALORANT").with(bearer(board)))
            .andExpect(status().isConflict)
            .andExpect(jsonPath("$.code").value("GameHoldsHistory"))

        // Nothing went: the game and its team are both still there.
        mvc.perform(get("/esports/games"))
            .andExpect(jsonPath("$[?(@.code == 'VALORANT')].name").value("Valorant"))
    }

    @Test
    fun `the reason a removal was refused says how much the game holds`() {
        val board = createUserWithRole(Role.BOARD)
        fieldATeamIn("GEOGUESSR")

        mvc.perform(delete("/esports/games/{game}", "GEOGUESSR").with(bearer(board)))
            .andExpect(jsonPath("$.code").value("GameHoldsHistory"))
            .andExpect(jsonPath("$.teams").value(1))
    }

    @Test
    fun `a code naming no game cannot be removed`() {
        val board = createUserWithRole(Role.BOARD)

        mvc.perform(delete("/esports/games/{game}", "PONG").with(bearer(board)))
            .andExpect(status().isBadRequest)
    }

    @Test
    fun `a member cannot remove a game`() {
        val member = createUserWithRole(Role.MEMBER)

        mvc.perform(delete("/esports/games/{game}", "SMASH").with(bearer(member)))
            .andExpect(status().isForbidden)
    }

    @Test
    fun `a member cannot rewrite a game`() {
        val member = createUserWithRole(Role.MEMBER)

        mvc.perform(
            put("/esports/games/{game}", "VALORANT")
                .with(bearer(member))
                .contentType(MediaType.APPLICATION_JSON)
                .content("""{"name":"Valorant","slug":"valorant","intro":"Mine now.","sortIndex":1,"fielded":true}"""),
        )
            .andExpect(status().isForbidden)
    }

    @Test
    fun `an anonymous visitor may read the games but not change one`() {
        mvc.perform(get("/esports/games")).andExpect(status().isOk)
        mvc.perform(
            put("/esports/games/{game}", "VALORANT")
                .contentType(MediaType.APPLICATION_JSON)
                .content("""{"name":"Valorant","slug":"valorant","intro":null,"sortIndex":1,"fielded":true}"""),
        )
            .andExpect(status().isUnauthorized)
    }
}

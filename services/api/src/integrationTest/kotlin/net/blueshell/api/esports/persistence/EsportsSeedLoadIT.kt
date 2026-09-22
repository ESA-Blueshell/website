package net.blueshell.api.esports.persistence

import net.blueshell.api.testsupport.EsportsSeedFixture
import net.blueshell.api.esports.domain.ShippedEsports
import net.blueshell.api.testsupport.UserTestSupport
import org.assertj.core.api.Assertions.assertThat
import org.junit.jupiter.api.AfterEach
import org.junit.jupiter.api.Test
import org.springframework.beans.factory.annotation.Autowired
import org.springframework.boot.test.context.SpringBootTest
import org.springframework.jdbc.core.JdbcTemplate
import javax.sql.DataSource

/**
 * Each case starts from the empty database this suite resets to, loads the seed files, and
 * checks what landed. Loading twice is the case that matters: the loader runs on every start,
 * on a database that already holds the history and the edits made to it since.
 *
 * The files loaded here are [EsportsSeedFixture], not the ones the site ships. What the loader
 * does is the subject; which teams the association fielded is not, and a roster somebody
 * remembers differently must not fail a test about upserts.
 *
 * `game` is the exception to the reset — the clean-up restores the games the migration
 * established, because other tables point at them — so a case about a game names its own code
 * rather than counting the table.
 */
@SpringBootTest
class EsportsSeedLoadIT : UserTestSupport() {
    @Autowired private lateinit var dataSource: DataSource

    @Autowired private lateinit var jdbc: JdbcTemplate

    private val tables = listOf("game", "season", "team", "team_roster_entry")

    private fun count(table: String): Int =
        jdbc.queryForObject("SELECT COUNT(*) FROM $table WHERE deleted_at = '9999-12-31 23:59:59'", Int::class.java)!!

    private fun gameExists(code: String): Boolean =
        jdbc.queryForObject(
            "SELECT COUNT(*) FROM game WHERE code = ? AND deleted_at = '9999-12-31 23:59:59'",
            Int::class.java,
            code,
        )!! > 0

    @Test
    fun `every record in the files lands`() {
        runLoader()

        assertThat(count("season")).isEqualTo(EsportsSeedFixture.SEASONS)
        // Three teams from four rows: Nomads is listed for Alpha and for Beta, and is one team
        // that changed the game it plays rather than two that share a name.
        assertThat(count("team")).isEqualTo(EsportsSeedFixture.TEAMS)
        assertThat(count("team_roster_entry")).isEqualTo(EsportsSeedFixture.ROSTER_PLACES)
        assertThat(EsportsSeedFixture.GAMES.filterNot(::gameExists)).isEmpty()
    }

    @Test
    fun `running the loader again changes nothing`() {
        runLoader()
        val before = tables.associateWith { count(it) }

        assertThat(runLoader()).isEqualTo(ShippedEsports.Applied(0, 0, 0, 0))

        assertThat(tables.associateWith { count(it) }).isEqualTo(before)
    }

    @Test
    fun `a game with nothing said about it is loaded like any other`() {
        runLoader()

        // Gamma has no accent and no intro, which is what a game nobody has written up looks
        // like, and it still fields the team the files give it.
        assertThat(
            jdbc.queryForObject(
                "SELECT COUNT(DISTINCT ts.team_id) FROM team_season ts JOIN team t ON t.id = ts.team_id" +
                    " WHERE ts.game = 'GAMMA' AND t.deleted_at = '9999-12-31 23:59:59'",
                Int::class.java,
            ),
        ).isGreaterThan(0)
    }

    /**
     * The colour, not the pictures. Both of a game's are uploads addressed by their contents,
     * so the migration writes the record and the start-up step puts the art on it — which is
     * asserted where that step is, in `ShippedArtIT`.
     */
    @Test
    fun `a game carries the name and the colour the file gives it`() {
        runLoader()

        val row = jdbc.queryForMap("SELECT name, slug, accent FROM game WHERE code = 'ALPHA'")
        assertThat(row["name"]).isEqualTo("Alpha")
        assertThat(row["slug"]).isEqualTo("alpha")
        assertThat(row["accent"]).isEqualTo("#112233")
    }

    @Test
    fun `a game nobody has drawn art for carries none rather than something invented`() {
        runLoader()

        // Gamma has no accent written for it. The island reads such a game on its own colour,
        // which it can only do if the record says there is none.
        val row = jdbc.queryForMap("SELECT accent FROM game WHERE code = 'GAMMA'")
        assertThat(row["accent"]).isNull()
    }

    @Test
    fun `a game edited on the site keeps the edit through the next run`() {
        runLoader()
        jdbc.update("UPDATE game SET name = 'Something Else', accent = NULL WHERE code = 'BETA'")

        runLoader()

        val row = jdbc.queryForMap("SELECT name, accent FROM game WHERE code = 'BETA'")
        assertThat(row["name"]).isEqualTo("Something Else")
        assertThat(row["accent"]).isNull()
    }

    @Test
    fun `a deleted game is left deleted rather than resurrected by the next run`() {
        runLoader()
        jdbc.update("UPDATE game SET deleted_at = NOW(6) WHERE code = 'GAMMA'")

        runLoader()

        assertThat(gameExists("GAMMA")).isFalse()
    }

    @Test
    fun `a team renamed on the site is not written again under the name in the file`() {
        runLoader()
        val name = jdbc.queryForObject("SELECT name FROM team ORDER BY id LIMIT 1", String::class.java)!!
        jdbc.update("UPDATE team SET name = CONCAT(name, ' (renamed)') WHERE name = ?", name)
        val before = tables.associateWith { count(it) }

        runLoader()

        assertThat(tables.associateWith { count(it) }).isEqualTo(before)
        assertThat(jdbc.queryForObject("SELECT COUNT(*) FROM team WHERE name = ?", Int::class.java, name)).isZero()
    }

    @Test
    fun `a season edited on the site keeps its dates through the next run`() {
        runLoader()
        val id = jdbc.queryForObject("SELECT id FROM season ORDER BY id LIMIT 1", Long::class.java)!!
        jdbc.update("UPDATE season SET end_date = '2000-01-01' WHERE id = ?", id)

        runLoader()

        assertThat(jdbc.queryForObject("SELECT end_date FROM season WHERE id = ?", String::class.java, id))
            .startsWith("2000-01-01")
    }

    @Test
    fun `a deleted team is left deleted rather than resurrected by the next run`() {
        runLoader()
        val teamId =
            jdbc.queryForObject(
                "SELECT t.id FROM team t JOIN team_season ts ON ts.team_id = t.id" +
                    " WHERE ts.game = 'GAMMA' AND t.deleted_at = '9999-12-31 23:59:59' ORDER BY t.id LIMIT 1",
                Long::class.java,
            )!!
        val name = jdbc.queryForObject("SELECT name FROM team WHERE id = ?", String::class.java, teamId)!!
        jdbc.update("UPDATE team SET deleted_at = NOW(6) WHERE id = ?", teamId)

        runLoader()

        // The file still lists it. An admin removing it is the later decision, and the next
        // edit anywhere in the file must not undo it.
        assertThat(
            jdbc.queryForObject(
                "SELECT COUNT(*) FROM team WHERE name = ? AND deleted_at = '9999-12-31 23:59:59'",
                Int::class.java,
                name,
            ),
        ).isZero()
    }

    @Test
    fun `a team dropped from a season is not fielded again by the next run`() {
        runLoader()
        val fielding =
            jdbc.queryForMap(
                "SELECT ts.id, ts.team_id, ts.season_id FROM team_season ts JOIN team t ON t.id = ts.team_id" +
                    " WHERE ts.game = 'GAMMA' AND ts.deleted_at = '9999-12-31 23:59:59' ORDER BY ts.id LIMIT 1",
            )
        val teamId = fielding.getValue("team_id") as Long
        val seasonId = fielding.getValue("season_id") as Long
        val played = rosterPlaces(teamId, seasonId)
        jdbc.update("UPDATE team_season SET deleted_at = NOW(6) WHERE id = ?", fielding.getValue("id"))

        runLoader()

        // The files still list the season the team played. Dropping it is the later decision,
        // and the next run must not undo it -- nor write a second fielding, which would carry
        // a second copy of a line-up the first one already holds.
        assertThat(
            jdbc.queryForObject(
                "SELECT COUNT(*) FROM team_season WHERE team_id = ? AND season_id = ?" +
                    " AND deleted_at = '9999-12-31 23:59:59'",
                Int::class.java,
                teamId,
                seasonId,
            ),
        ).isZero()
        assertThat(rosterPlaces(teamId, seasonId)).isEqualTo(played)
    }

    /** Every line-up place written for a team in a season, whichever fielding holds it. */
    private fun rosterPlaces(
        teamId: Long,
        seasonId: Long,
    ): Int =
        jdbc.queryForObject(
            "SELECT COUNT(*) FROM team_roster_entry e JOIN team_season ts ON ts.id = e.team_season_id" +
                " WHERE ts.team_id = ? AND ts.season_id = ? AND e.deleted_at = '9999-12-31 23:59:59'",
            Int::class.java,
            teamId,
            seasonId,
        )!!

    @Test
    fun `a roster entry edited on the site keeps the edit through the next run`() {
        runLoader()
        val entryId =
            jdbc.queryForObject(
                "SELECT id FROM team_roster_entry WHERE handle = 'two' AND deleted_at = '9999-12-31 23:59:59'" +
                    " ORDER BY id LIMIT 1",
                Long::class.java,
            )!!
        jdbc.update("UPDATE team_roster_entry SET sort_index = 99, team_role = 'COACH' WHERE id = ?", entryId)

        runLoader()

        val row = jdbc.queryForMap("SELECT sort_index, team_role FROM team_roster_entry WHERE id = ?", entryId)
        assertThat(row["sort_index"]).isEqualTo(99)
        assertThat(row["team_role"]).isEqualTo("COACH")
    }

    @Test
    fun `a roster entry whose handle was corrected is not written again under the old one`() {
        runLoader()
        jdbc.update("UPDATE team_roster_entry SET handle = 'two-corrected' WHERE handle = 'two'")
        val before = count("team_roster_entry")

        runLoader()

        assertThat(count("team_roster_entry")).isEqualTo(before)
    }

    private fun runLoader(): ShippedEsports.Applied =
        ShippedEsports(dataSource, transactionTemplate, EsportsSeedFixture.files).apply()

    @AfterEach
    fun forgetTheFixtureGames() {
        EsportsSeedFixture.forget(dataSource)
    }
}

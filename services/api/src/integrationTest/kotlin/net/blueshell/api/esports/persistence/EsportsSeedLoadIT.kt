package net.blueshell.api.esports.persistence

import db.migration.R__Esports_seed
import net.blueshell.api.testsupport.EsportsSeedFixture
import net.blueshell.api.testsupport.UserTestSupport
import org.assertj.core.api.Assertions.assertThat
import org.flywaydb.core.api.migration.Context
import org.junit.jupiter.api.AfterEach
import org.junit.jupiter.api.Test
import org.springframework.beans.factory.annotation.Autowired
import org.springframework.boot.test.context.SpringBootTest
import org.springframework.jdbc.core.JdbcTemplate
import java.sql.Connection
import javax.sql.DataSource

/**
 * Each case starts from the empty database this suite resets to, loads the seed files, and
 * checks what landed. Loading twice is the case that matters: the loader runs on every deploy
 * whose files have moved, on a database that already holds the history.
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

        runLoader()

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
    fun `a game renamed in the file is renamed on the next run`() {
        runLoader()
        jdbc.update("UPDATE game SET name = 'Something Else' WHERE code = 'BETA'")

        runLoader()

        // The files are the reviewed record, the same way they are for a roster entry.
        assertThat(jdbc.queryForObject("SELECT name FROM game WHERE code = 'BETA'", String::class.java))
            .isEqualTo("Beta")
    }

    @Test
    fun `a game the file lists is brought back, unlike everything else the file lists`() {
        runLoader()
        jdbc.update("UPDATE game SET deleted_at = NOW(6) WHERE code = 'GAMMA'")

        runLoader()

        // A game is what a team points at, so the file listing one is the statement that it
        // exists. A team or a roster entry is the other way round: removing it is a decision
        // the next run leaves alone.
        assertThat(gameExists("GAMMA")).isTrue()
    }

    @Test
    fun `a deleted team is left deleted rather than resurrected by the next run`() {
        runLoader()
        val teamId = jdbc.queryForObject(
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
        val fielding = jdbc.queryForMap(
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
    private fun rosterPlaces(teamId: Long, seasonId: Long): Int =
        jdbc.queryForObject(
            "SELECT COUNT(*) FROM team_roster_entry e JOIN team_season ts ON ts.id = e.team_season_id" +
                " WHERE ts.team_id = ? AND ts.season_id = ? AND e.deleted_at = '9999-12-31 23:59:59'",
            Int::class.java,
            teamId,
            seasonId,
        )!!

    @Test
    fun `a corrected row is applied on the next run`() {
        runLoader()
        val entryId = jdbc.queryForObject(
            "SELECT id FROM team_roster_entry WHERE handle = 'two' AND deleted_at = '9999-12-31 23:59:59'" +
                " ORDER BY id LIMIT 1",
            Long::class.java,
        )!!
        val original =
            jdbc.queryForObject("SELECT sort_index FROM team_roster_entry WHERE id = ?", Int::class.java, entryId)!!
        jdbc.update("UPDATE team_roster_entry SET sort_index = 99 WHERE id = ?", entryId)

        runLoader()

        // The files are the reviewed record, so the database is brought back to what they say.
        assertThat(
            jdbc.queryForObject("SELECT sort_index FROM team_roster_entry WHERE id = ?", Int::class.java, entryId),
        ).isEqualTo(original)
    }

    private fun runLoader() {
        dataSource.connection.use { connection ->
            R__Esports_seed(EsportsSeedFixture.files).migrate(object : Context {
                override fun getConfiguration() = null

                override fun getConnection(): Connection = connection
            })
        }
    }

    @AfterEach
    fun forgetTheFixtureGames() {
        EsportsSeedFixture.forget(dataSource)
    }
}

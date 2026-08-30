package net.blueshell.api.esports.persistence

import db.migration.R__Esports_seed
import net.blueshell.api.testsupport.UserTestSupport
import org.assertj.core.api.Assertions.assertThat
import org.flywaydb.core.api.migration.Context
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
 */
@SpringBootTest
class EsportsSeedLoadIT : UserTestSupport() {

    @Autowired private lateinit var dataSource: DataSource

    @Autowired private lateinit var jdbc: JdbcTemplate

    private val tables = listOf("game_page", "season", "team", "team_roster_entry")

    private fun count(table: String): Int =
        jdbc.queryForObject("SELECT COUNT(*) FROM $table WHERE deleted_at = '9999-12-31 23:59:59'", Int::class.java)!!

    @Test
    fun `every record in the files lands`() {
        runLoader()

        // The history the pages published, recovered: eight games, twelve seasons, twenty-seven
        // teams and five hundred and twenty-six appearances.
        assertThat(count("game_page")).isEqualTo(8)
        assertThat(count("season")).isEqualTo(12)
        // 26 teams from 27 rows: BS HyperS is listed for CS:GO and for CS2, and is one team
        // that changed the game it plays rather than two that share a name.
        assertThat(count("team")).isEqualTo(26)
        assertThat(count("team_roster_entry")).isEqualTo(526)
    }

    @Test
    fun `running the loader again changes nothing`() {
        runLoader()
        val before = tables.associateWith { count(it) }

        runLoader()

        assertThat(tables.associateWith { count(it) }).isEqualTo(before)
    }

    @Test
    fun `a game with no page of its own is loaded like any other`() {
        runLoader()

        // CS:GO and Smash are history with nothing rendering them, which is exactly why the
        // files have to carry them.
        assertThat(count("team")).isGreaterThan(0)
        assertThat(
            jdbc.queryForObject(
                "SELECT COUNT(DISTINCT ts.team_id) FROM team_season ts JOIN team t ON t.id = ts.team_id" +
                    " WHERE ts.game IN ('CSGO', 'SMASH') AND t.deleted_at = '9999-12-31 23:59:59'",
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

        val row = jdbc.queryForMap("SELECT name, slug, accent FROM game_page WHERE game = 'VALORANT'")
        assertThat(row["name"]).isEqualTo("Valorant")
        assertThat(row["slug"]).isEqualTo("valorant")
        assertThat(row["accent"]).isEqualTo("#ff4655")
    }

    @Test
    fun `a game nobody has drawn art for carries none rather than something invented`() {
        runLoader()

        // Trackmania has never had an accent written for it. The island reads such a game on
        // its own colour, which it can only do if the record says there is none.
        val row = jdbc.queryForMap("SELECT accent FROM game_page WHERE game = 'TRACKMANIA'")
        assertThat(row["accent"]).isNull()
    }

    @Test
    fun `a game renamed in the file is renamed on the next run`() {
        runLoader()
        jdbc.update("UPDATE game_page SET name = 'Something Else' WHERE game = 'GEOGUESSR'")

        runLoader()

        // The files are the reviewed record, the same way they are for a roster entry.
        assertThat(jdbc.queryForObject("SELECT name FROM game_page WHERE game = 'GEOGUESSR'", String::class.java))
            .isEqualTo("GeoGuessr")
    }

    @Test
    fun `a game the file lists is brought back, unlike everything else the file lists`() {
        runLoader()
        jdbc.update("UPDATE game_page SET deleted_at = NOW(6) WHERE game = 'SMASH'")

        runLoader()

        // A game is what a team points at, so the file listing one is the statement that it
        // exists. A team or a roster entry is the other way round: removing it is a decision
        // the next run leaves alone.
        assertThat(count("game_page")).isEqualTo(8)
    }

    @Test
    fun `a deleted team is left deleted rather than resurrected by the next run`() {
        runLoader()
        val teamId = jdbc.queryForObject(
            "SELECT t.id FROM team t JOIN team_season ts ON ts.team_id = t.id" +
                " WHERE ts.game = 'SMASH' AND t.deleted_at = '9999-12-31 23:59:59' ORDER BY t.id LIMIT 1",
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
                " WHERE ts.game = 'SMASH' AND ts.deleted_at = '9999-12-31 23:59:59' ORDER BY ts.id LIMIT 1",
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
            "SELECT id FROM team_roster_entry WHERE handle = 'BSKingCookie' AND deleted_at = '9999-12-31 23:59:59'" +
                " ORDER BY id LIMIT 1",
            Long::class.java,
        )!!
        val original =
            jdbc.queryForObject("SELECT sort_index FROM team_roster_entry WHERE id = ?", Int::class.java, entryId)!!
        jdbc.update("UPDATE team_roster_entry SET sort_index = 99 WHERE id = ?", entryId)

        runLoader()

        // The files are the reviewed record, so the database is brought back to what they say.
        assertThat(jdbc.queryForObject("SELECT sort_index FROM team_roster_entry WHERE id = ?", Int::class.java, entryId))
            .isEqualTo(original)
    }

    private fun runLoader() {
        dataSource.connection.use { connection ->
            R__Esports_seed().migrate(object : Context {
                override fun getConfiguration() = null

                override fun getConnection(): Connection = connection
            })
        }
    }
}

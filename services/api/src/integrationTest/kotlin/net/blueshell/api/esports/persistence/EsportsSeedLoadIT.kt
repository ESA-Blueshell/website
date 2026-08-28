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
        assertThat(count("team")).isEqualTo(27)
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
                "SELECT COUNT(*) FROM team WHERE game IN ('CSGO', 'SMASH') AND deleted_at = '9999-12-31 23:59:59'",
                Int::class.java,
            ),
        ).isGreaterThan(0)
    }

    @Test
    fun `a game carries the name and the art the file gives it`() {
        runLoader()

        val row = jdbc.queryForMap("SELECT name, slug, accent, mark, banner FROM game_page WHERE game = 'VALORANT'")
        assertThat(row["name"]).isEqualTo("Valorant")
        assertThat(row["slug"]).isEqualTo("valorant")
        assertThat(row["accent"]).isEqualTo("#ff4655")
        assertThat(row["mark"]).isEqualTo("valorant.png")
        assertThat(row["banner"]).isEqualTo("valorantesports1.jpg")
    }

    @Test
    fun `a game nobody has drawn art for carries none rather than something invented`() {
        runLoader()

        // No accent or icon has ever been chosen for Trackmania. The frontend falls back to the
        // brand colour, which it can only do if the record says the fields are empty.
        val row = jdbc.queryForMap("SELECT accent, mark, banner FROM game_page WHERE game = 'TRACKMANIA'")
        assertThat(row["accent"]).isNull()
        assertThat(row["mark"]).isNull()
        assertThat(row["banner"]).isNull()
    }

    @Test
    fun `a game renamed in the file is renamed on the next run`() {
        runLoader()
        jdbc.update("UPDATE game_page SET name = 'Something Else' WHERE game = 'GEOGUESSR'")

        runLoader()

        // The files are authoritative, the same as for a roster entry.
        assertThat(jdbc.queryForObject("SELECT name FROM game_page WHERE game = 'GEOGUESSR'", String::class.java))
            .isEqualTo("GeoGuessr")
    }

    @Test
    fun `a game the file lists is brought back, unlike everything else the file lists`() {
        runLoader()
        jdbc.update("UPDATE game_page SET deleted_at = NOW(6) WHERE game = 'SMASH'")

        runLoader()

        // Teams reference a game, so a row in the file means the game must exist. Teams and roster
        // entries work the other way round: deleting one is deliberate, and the next run respects it.
        assertThat(count("game_page")).isEqualTo(8)
    }

    @Test
    fun `a deleted team is left deleted rather than resurrected by the next run`() {
        runLoader()
        val teamId = jdbc.queryForObject(
            "SELECT id FROM team WHERE game = 'SMASH' AND deleted_at = '9999-12-31 23:59:59' ORDER BY id LIMIT 1",
            Long::class.java,
        )!!
        val name = jdbc.queryForObject("SELECT name FROM team WHERE id = ?", String::class.java, teamId)!!
        jdbc.update("UPDATE team SET deleted_at = NOW(6) WHERE id = ?", teamId)

        runLoader()

        // The file still lists it. An admin removing it is the later decision, and the next
        // edit anywhere in the file must not undo it.
        assertThat(
            jdbc.queryForObject(
                "SELECT COUNT(*) FROM team WHERE game = 'SMASH' AND name = ? AND deleted_at = '9999-12-31 23:59:59'",
                Int::class.java,
                name,
            ),
        ).isZero()
    }

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

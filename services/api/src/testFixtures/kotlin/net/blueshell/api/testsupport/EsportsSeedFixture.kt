package net.blueshell.api.testsupport

import net.blueshell.api.shared.seed.SeedCsv
import javax.sql.DataSource

/**
 * The seed a test loads instead of the one the site ships.
 *
 * The shipped files are the association's history: rows are added and corrected as members
 * remember them, so a test that counts them fails on a data edit that broke nothing. These
 * files are small enough to read in one screen and change only when a behaviour does.
 *
 * Three games, two seasons, three teams from four rows — Nomads is listed under two games, as
 * one team that changed the game it plays — and six line-up places. Gamma is the game with
 * nothing said about it: no accent, no intro, and a team fielded in it all the same.
 */
object EsportsSeedFixture {

    val files = SeedCsv("db/seed/esports-fixtures")

    /** What the files say, as the numbers a test asserts against. */
    const val SEASONS = 2
    const val TEAMS = 3
    const val ROSTER_PLACES = 6

    /** The games the files list, in the order they are shown. */
    val GAMES = listOf("ALPHA", "BETA", "GAMMA")

    /**
     * Takes the fixture games back out.
     *
     * `game` is reference data to the clean-up between tests: it deletes the table and puts back
     * what it read the first time it ran. A fixture game left behind is therefore read as the
     * migration's own on the next run in the same database, and every test that counts the games
     * fails from then on. So a suite that loads these files removes them when it is done.
     */
    fun forget(dataSource: DataSource) {
        dataSource.connection.use { connection ->
            connection.createStatement().use { statement ->
                statement.execute("SET FOREIGN_KEY_CHECKS = 0")
                connection.prepareStatement(
                    "DELETE FROM game WHERE code IN (${GAMES.joinToString(", ") { "?" }})",
                ).use { delete ->
                    GAMES.forEachIndexed { index, code -> delete.setString(index + 1, code) }
                    delete.executeUpdate()
                }
                statement.execute("SET FOREIGN_KEY_CHECKS = 1")
            }
        }
    }
}

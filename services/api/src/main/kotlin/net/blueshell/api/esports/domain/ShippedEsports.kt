package net.blueshell.api.esports.domain

import net.blueshell.api.shared.seed.SeedCsv
import net.blueshell.api.shared.seed.SeedLedger
import net.blueshell.api.shared.seed.SeedOrder
import org.slf4j.LoggerFactory
import org.springframework.boot.context.event.ApplicationReadyEvent
import org.springframework.context.event.EventListener
import org.springframework.core.annotation.Order
import org.springframework.jdbc.datasource.DataSourceUtils
import org.springframework.stereotype.Component
import org.springframework.transaction.support.TransactionTemplate
import java.sql.Connection
import java.sql.Date
import java.sql.Statement
import java.sql.Types
import javax.sql.DataSource

/**
 * Adds the esports history in `db/seed/esports` that the database has never had, and leaves
 * every row it has alone. Each row is written once, on the first start that finds it (see
 * [SeedLedger]), so an edit, rename or deletion made on the site outlives every later start.
 */
@Component
class ShippedEsports(
    private val dataSource: DataSource,
    private val transactions: TransactionTemplate,
    // Defaulted so tests can pass their own, as the migration allowed.
    private val seed: SeedCsv = EsportsSeed.files,
) {
    /** The rows a run wrote, which is none at all once the files have been applied. */
    data class Applied(
        val games: Int,
        val seasons: Int,
        val teams: Int,
        val entries: Int,
    )

    // One transaction, as the migration had. DataSourceUtils returns the connection it is bound to.
    fun apply(): Applied =
        transactions.execute {
            val connection = DataSourceUtils.getConnection(dataSource)
            try {
                load(connection)
            } finally {
                DataSourceUtils.releaseConnection(connection, dataSource)
            }
        }

    private fun load(connection: Connection): Applied {
        val ledger = SeedLedger(connection, SEED)
        // The games come first: a team names one, and the database enforces that it exists.
        val games = parse(read("games.csv")).count { row -> addGame(connection, ledger, row) }
        val seasons = parse(read("seasons.csv"))
        val teams = parse(read("teams.csv"))
        val roster = parse(read("roster.csv"))

        val seasonsAdded = seasons.count { row -> addSeason(connection, ledger, row) }
        // By name alone: the file lists a team once per game it played, because the art is per
        // game, but those rows are one team.
        val teamNames = teams.map { row -> row.getValue("name") }.distinct()
        val teamsAdded = teamNames.count { name -> addTeam(connection, ledger, name) }
        val seasonIds = seasons.associate { row -> row.getValue("name").let { it to activeId(connection, SEASON, it) } }
        val teamIds = teamNames.associateWith { name -> activeId(connection, TEAM, name) }

        val entries =
            roster.count { row ->
                val teamId = teamIds[row.getValue("team")]
                val seasonId = seasonIds[row.getValue("season")]
                // A team or season that is deleted or renamed leaves its line-up with nowhere to go.
                teamId != null && seasonId != null && addEntry(connection, ledger, teamId, seasonId, row)
            }
        // Only where places were written: whoever was just attached to one is the only member
        // who can have a handle to take up.
        val handles = if (entries > 0) adoptHandlesPlayedUnder(connection) else 0
        if (handles > 0) log.info("[esports-seed] {} members took up the handle they last played under", handles)
        val applied = Applied(games = games, seasons = seasonsAdded, teams = teamsAdded, entries = entries)
        if (applied != Applied(0, 0, 0, 0)) {
            log.info(
                "[esports-seed] {} games, {} seasons, {} teams and {} roster entries added",
                games,
                seasonsAdded,
                teamsAdded,
                entries,
            )
        }
        return applied
    }

    /**
     * A game, keyed on its code. Whether the association still fields it is derived from the
     * seasons; whether it is archived is the file's to say once, since the site edits it after.
     */
    private fun addGame(
        connection: Connection,
        ledger: SeedLedger,
        row: Map<String, String>,
    ): Boolean {
        val code = row.getValue("code")
        val archived = row["archived"].toBoolean()
        // Its own key, so a game already standing when archiving shipped is archived once too.
        if (archived && ledger.toWrite("game-archived|$code") { false }) {
            connection.prepareStatement("UPDATE game SET archived = TRUE WHERE code = ?").use { statement ->
                statement.setString(1, code)
                statement.executeUpdate()
            }
        }
        if (!ledger.toWrite("game|$code") { exists(connection, "SELECT id FROM game WHERE code = ?", code) }) {
            return false
        }
        connection
            .prepareStatement("INSERT INTO game (code, name, slug, accent, sort_index, intro, archived) VALUES (?, ?, ?, ?, ?, ?, ?)")
            .use { statement ->
                listOf<Any?>(
                    code,
                    row.getValue("name"),
                    row.getValue("slug"),
                    row.getValue("accent").ifBlank { null },
                    row.getValue("sort_index").toInt(),
                    row.getValue("intro").ifBlank { null },
                    archived,
                ).forEachIndexed { index, value -> statement.setObject(index + 1, value) }
                statement.executeUpdate()
            }
        return true
    }

    private fun addSeason(
        connection: Connection,
        ledger: SeedLedger,
        row: Map<String, String>,
    ): Boolean {
        val name = row.getValue("name")
        if (!ledger.toWrite("season|$name") { exists(connection, SEASON, name) }) {
            return false
        }
        connection.prepareStatement("INSERT INTO season (name, start_date, end_date) VALUES (?, ?, ?)").use { statement ->
            statement.setString(1, name)
            statement.setDate(2, Date.valueOf(row.getValue("start_date")))
            statement.setDate(3, Date.valueOf(row.getValue("end_date")))
            statement.executeUpdate()
        }
        return true
    }

    /**
     * A team, keyed on its name alone. The pool is the association's rather than a game's, so
     * BS HyperS listed once for CS:GO and once for CS2 is one team, drawn with each game's art.
     */
    private fun addTeam(
        connection: Connection,
        ledger: SeedLedger,
        name: String,
    ): Boolean {
        if (!ledger.toWrite("team|$name") { exists(connection, TEAM, name) }) {
            return false
        }
        connection.prepareStatement("INSERT INTO team (name) VALUES (?)").use { statement ->
            statement.setString(1, name)
            statement.executeUpdate()
        }
        return true
    }

    /** A line-up place, keyed on the team, game and season it was played in and the handle. */
    private fun addEntry(
        connection: Connection,
        ledger: SeedLedger,
        teamId: Long,
        seasonId: Long,
        row: Map<String, String>,
    ): Boolean {
        val game = row.getValue("game")
        val handle = row.getValue("handle")
        val key = listOf("entry", row.getValue("team"), game, row.getValue("season"), handle).joinToString("|")
        // Through whatever fielding holds it, dropped or not: looking only under a live
        // fielding would miss a dropped team's line-up and write the row a second time.
        val standing = {
            connection
                .prepareStatement(
                    """
                    SELECT e.id FROM team_roster_entry e
                    JOIN team_season ts ON ts.id = e.team_season_id
                    WHERE ts.team_id = ? AND ts.game = ? AND ts.season_id = ? AND e.handle = ?
                    """.trimIndent(),
                ).use { statement ->
                    statement.setLong(1, teamId)
                    statement.setString(2, game)
                    statement.setLong(3, seasonId)
                    statement.setString(4, handle)
                    statement.executeQuery().use { rows -> rows.next() }
                }
        }
        if (!ledger.toWrite(key, standing)) return false

        // Only now, on the path that actually writes somebody down. Fielding the team before
        // this point would field it on every run, undoing a board that dropped it.
        val fieldingId = fieldTeam(connection, teamId, game, seasonId)
        val displayName = row.getValue("display_name").ifBlank { null }
        connection
            .prepareStatement(
                """
                INSERT INTO team_roster_entry
                    (team_season_id, handle, team_role, display_name, sort_index, user_id)
                VALUES (?, ?, ?, ?, ?, ?)
                """.trimIndent(),
            ).use { statement ->
                statement.setLong(1, fieldingId)
                statement.setString(2, handle)
                statement.setString(3, row.getValue("role"))
                statement.setString(4, displayName)
                statement.setInt(5, row.getValue("sort_index").toInt())
                // Attached as the place is created, which is the only moment this can be settled
                // without overruling somebody.
                val memberId = displayName?.let { memberNamed(connection, it) }
                if (memberId == null) statement.setNull(6, Types.BIGINT) else statement.setLong(6, memberId)
                statement.executeUpdate()
            }
        return true
    }

    /**
     * The one member who answers to a name exactly, or nobody.
     *
     * Built the way the site writes a name, prefix and all. A name matching nobody, or more than
     * one person, leaves the place standing under its handle for an admin to resolve: guessing
     * between two people is worse than leaving it.
     */
    private fun memberNamed(
        connection: Connection,
        name: String,
    ): Long? =
        connection
            .prepareStatement(
                """
                SELECT MIN(u.id) FROM users u
                WHERE TRIM(CONCAT_WS(' ', u.first_name, u.prefix, u.last_name)) = ? AND u.$ACTIVE
                HAVING COUNT(*) = 1
                """.trimIndent(),
            ).use { statement ->
                statement.setString(1, name)
                statement.executeQuery().use { rows ->
                    if (!rows.next()) return null
                    val id = rows.getLong(1)
                    if (rows.wasNull()) null else id
                }
            }

    /**
     * Gives a member just attached to a place the handle they last played that game under.
     *
     * A member's handle for a game renders them in every season of it, and the most recent
     * season they played is the one that names them. A handle somebody has already set is left
     * alone. Runs only where places were written, so a start that changed nothing writes nothing.
     */
    private fun adoptHandlesPlayedUnder(connection: Connection): Int =
        connection
            .prepareStatement(
                """
                INSERT INTO user_game_account (user_id, game, handle)
                SELECT x.user_id, x.game, x.handle
                FROM (
                    SELECT e.user_id, ts.game, e.handle,
                           ROW_NUMBER() OVER (
                               PARTITION BY e.user_id, ts.game ORDER BY s.start_date DESC, e.id DESC
                           ) AS rn
                    FROM team_roster_entry e
                    JOIN team_season ts ON ts.id = e.team_season_id
                    JOIN season s ON s.id = ts.season_id
                    WHERE e.user_id IS NOT NULL AND e.$ACTIVE
                ) x
                WHERE x.rn = 1
                  AND NOT EXISTS (
                    SELECT 1 FROM user_game_account a
                    WHERE a.user_id = x.user_id AND a.game = x.game AND a.$ACTIVE)
                """.trimIndent(),
            ).use { statement -> statement.executeUpdate() }

    /** Records that a game ran in a season, unless it already says so. */
    private fun enterGame(
        connection: Connection,
        game: String,
        seasonId: Long,
    ) {
        connection
            .prepareStatement(
                "SELECT id FROM season_game WHERE season_id = ? AND game = ? AND $ACTIVE",
            ).use { statement ->
                statement.setLong(1, seasonId)
                statement.setString(2, game)
                statement.executeQuery().use { rows -> if (rows.next()) return }
            }
        connection.prepareStatement("INSERT INTO season_game (season_id, game) VALUES (?, ?)").use { statement ->
            statement.setLong(1, seasonId)
            statement.setString(2, game)
            statement.executeUpdate()
        }
    }

    private fun fieldTeam(
        connection: Connection,
        teamId: Long,
        game: String,
        seasonId: Long,
    ): Long {
        // A team playing a game in a season says that game ran that season.
        enterGame(connection, game, seasonId)
        connection
            .prepareStatement(
                "SELECT id FROM team_season WHERE team_id = ? AND game = ? AND season_id = ? AND $ACTIVE",
            ).use { statement ->
                statement.setLong(1, teamId)
                statement.setString(2, game)
                statement.setLong(3, seasonId)
                statement.executeQuery().use { rows -> if (rows.next()) return rows.getLong(1) }
            }
        connection
            .prepareStatement(
                "INSERT INTO team_season (team_id, game, season_id) VALUES (?, ?, ?)",
                Statement.RETURN_GENERATED_KEYS,
            ).use { statement ->
                statement.setLong(1, teamId)
                statement.setString(2, game)
                statement.setLong(3, seasonId)
                statement.executeUpdate()
                statement.generatedKeys.use { keys ->
                    check(keys.next()) { "Fielding team $teamId in season $seasonId returned no id" }
                    return keys.getLong(1)
                }
            }
    }

    private fun activeId(
        connection: Connection,
        sql: String,
        name: String,
    ): Long? =
        connection.prepareStatement("$sql AND $ACTIVE").use { statement ->
            statement.setString(1, name)
            statement.executeQuery().use { rows -> if (rows.next()) rows.getLong(1) else null }
        }

    private fun exists(
        connection: Connection,
        sql: String,
        value: String,
    ): Boolean =
        connection.prepareStatement(sql).use { statement ->
            statement.setString(1, value)
            statement.executeQuery().use { rows -> rows.next() }
        }

    private fun read(name: String): String = seed.read(name)

    companion object {
        private val log = LoggerFactory.getLogger(ShippedEsports::class.java)

        /** The sentinel a live row carries, as every soft-deleted table here uses it. */
        private const val ACTIVE = "deleted_at = '9999-12-31 23:59:59'"

        private const val SEED = "esports"
        private const val SEASON = "SELECT id FROM season WHERE name = ?"
        private const val TEAM = "SELECT id FROM team WHERE name = ?"

        fun parse(content: String): List<Map<String, String>> = SeedCsv.parse(content)
    }
}

/**
 * A separate bean so the transaction is opened by the proxy, and a failure never blocks start-up.
 */
@Component
class ShippedEsportsOnStartup(
    private val esports: ShippedEsports,
) {
    @Order(SeedOrder.RECORDS)
    @EventListener(ApplicationReadyEvent::class)
    fun onReady() {
        try {
            esports.apply()
        } catch (e: Exception) {
            log.warn("[esports-seed] could not load the esports history that ships: {}", e.message)
        }
    }

    private companion object {
        val log = LoggerFactory.getLogger(ShippedEsportsOnStartup::class.java)
    }
}

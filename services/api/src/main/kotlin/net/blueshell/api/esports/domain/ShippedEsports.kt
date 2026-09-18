package net.blueshell.api.esports.domain

import net.blueshell.api.shared.seed.SeedCsv
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
 * Loads the esports history from `db/seed/esports`, which is its only record. Upserts on the
 * recorded name, deletion outranks the files, and an attached account is never re-matched.
 */
@Component
class ShippedEsports(
    private val dataSource: DataSource,
    private val transactions: TransactionTemplate,
    // Defaulted so tests can pass their own, as the migration allowed.
    private val seed: SeedCsv = EsportsSeed.files,
) {
    data class Applied(
        val games: Int,
        val seasons: Int,
        val teams: Int,
        val entries: Int,
        val leftDeleted: Int,
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
        }!!

    private fun load(connection: Connection): Applied {
        // The games come first: a team names one, and the database now enforces that it exists.
        val games = parse(read("games.csv"))
        games.forEach { row -> upsertGame(connection, row) }
        val seasons = parse(read("seasons.csv"))
        val teams = parse(read("teams.csv"))
        val roster = parse(read("roster.csv"))

        val seasonIds = seasons.associate { row -> row.getValue("name") to upsertSeason(connection, row) }
        // Keyed by name alone: the file lists a team once per game it played, because the art is
        // per game, but those rows are one team.
        val teamIds =
            teams
                .map { row -> row.getValue("name") }
                .distinct()
                .associateWith { name -> upsertTeam(connection, name) }

        var written = 0
        var skipped = 0
        roster.forEach { row ->
            val teamId = teamIds[row.getValue("team")]
            val seasonId = seasonIds[row.getValue("season")]
            if (teamId == null || seasonId == null) {
                // The team or season it belongs to is deleted, so the entry has nowhere to go.
                skipped += 1
                return@forEach
            }
            val game = row.getValue("game")
            if (upsertEntry(connection, teamId, game, seasonId, row)) written += 1 else skipped += 1
        }
        // Only where places were written: whoever was just attached to one is the only member
        // who can have a handle to take up.
        val handles = if (written > 0) adoptHandlesPlayedUnder(connection) else 0
        if (handles > 0) log.info("[esports-seed] {} members took up the handle they last played under", handles)
        log.info(
            "[esports-seed] {} games, {} seasons, {} teams, {} roster entries applied ({} left to their deletion)",
            games.size,
            seasonIds.size,
            teamIds.size,
            written,
            skipped,
        )
        return Applied(
            games = games.size,
            seasons = seasonIds.size,
            teams = teamIds.size,
            entries = written,
            leftDeleted = skipped,
        )
    }

    /**
     * A game as the file has it: what it is called, the address it answers to, the art it
     * carries. Whether the association still plays it is derived from the seasons, not recorded here.
     *
     * The code is the identity and is never rewritten. Unlike the rest of the seed a deleted game
     * is not left deleted: a game is what a team points at, so a row in the file is the statement
     * that the game exists.
     */
    private fun upsertGame(
        connection: Connection,
        row: Map<String, String>,
    ) {
        val code = row.getValue("code")
        val name = row.getValue("name")
        val slug = row.getValue("slug")
        val accent = row.getValue("accent").ifBlank { null }
        val sortIndex = row.getValue("sort_index").toInt()
        val intro = row.getValue("intro").ifBlank { null }

        // Found whether or not it is deleted: a code is unique across every row, so there is no
        // second row to insert beside a deleted one. A game the file lists exists, so a deleted
        // row is brought back rather than duplicated.
        val existing = activeId(connection, "SELECT id FROM game WHERE code = ?", code)
        val fields = listOf<Any?>(name, slug, accent, sortIndex, intro)
        if (existing != null) {
            connection
                .prepareStatement(
                    """
                    UPDATE game
                    SET name = ?, slug = ?, accent = ?, sort_index = ?, intro = ?,
                        deleted_at = '9999-12-31 23:59:59'
                    WHERE id = ?
                      AND NOT (name <=> ? AND slug <=> ? AND accent <=> ?
                               AND sort_index <=> ? AND intro <=> ? AND $ACTIVE)
                    """.trimIndent(),
                ).use { statement ->
                    fields.forEachIndexed { index, value -> statement.setObject(index + 1, value) }
                    statement.setLong(fields.size + 1, existing)
                    fields.forEachIndexed { index, value -> statement.setObject(index + fields.size + 2, value) }
                    statement.executeUpdate()
                }
            return
        }
        connection
            .prepareStatement(
                """
                INSERT INTO game (code, name, slug, accent, sort_index, intro)
                VALUES (?, ?, ?, ?, ?, ?)
                """.trimIndent(),
            ).use { statement ->
                (listOf<Any?>(code) + fields)
                    .forEachIndexed { index, value -> statement.setObject(index + 1, value) }
                statement.executeUpdate()
            }
    }

    private fun upsertSeason(
        connection: Connection,
        row: Map<String, String>,
    ): Long? {
        val name = row.getValue("name")
        val existing = activeId(connection, "SELECT id FROM season WHERE name = ? AND $ACTIVE", name)
        if (existing == null && isDeleted(connection, "SELECT id FROM season WHERE name = ? AND NOT $ACTIVE", name)) {
            return null
        }
        val start = Date.valueOf(row.getValue("start_date"))
        val end = Date.valueOf(row.getValue("end_date"))
        if (existing != null) {
            connection
                .prepareStatement(
                    "UPDATE season SET start_date = ?, end_date = ? WHERE id = ? AND (start_date <> ? OR end_date <> ?)",
                ).use { statement ->
                    statement.setDate(1, start)
                    statement.setDate(2, end)
                    statement.setLong(3, existing)
                    statement.setDate(4, start)
                    statement.setDate(5, end)
                    statement.executeUpdate()
                }
            return existing
        }
        connection.prepareStatement("INSERT INTO season (name, start_date, end_date) VALUES (?, ?, ?)").use { statement ->
            statement.setString(1, name)
            statement.setDate(2, start)
            statement.setDate(3, end)
            statement.executeUpdate()
        }
        return activeId(connection, "SELECT id FROM season WHERE name = ? AND $ACTIVE", name)
    }

    /**
     * A team, found or written by name alone.
     *
     * The pool is the association's rather than a game's, so a name names one team however many
     * games it plays: BS HyperS is listed once for CS:GO and once for CS2 in the file, and both
     * rows mean the same team, drawn with that game's own art.
     */
    private fun upsertTeam(
        connection: Connection,
        name: String,
    ): Long? {
        val find = "SELECT id FROM team WHERE name = ?"
        val existing = activeId(connection, "$find AND $ACTIVE", name)
        if (existing == null && isDeleted(connection, "$find AND NOT $ACTIVE", name)) return null
        if (existing != null) return existing
        connection.prepareStatement("INSERT INTO team (name) VALUES (?)").use { statement ->
            statement.setString(1, name)
            statement.executeUpdate()
        }
        return activeId(connection, "$find AND $ACTIVE", name)
    }

    /** True when the entry now says what the file says; false when it is left to its deletion. */
    private fun upsertEntry(
        connection: Connection,
        teamId: Long,
        game: String,
        seasonId: Long,
        row: Map<String, String>,
    ): Boolean {
        val handle = row.getValue("handle")
        val role = row.getValue("role")
        val displayName = row.getValue("display_name").ifBlank { null }
        val sortIndex = row.getValue("sort_index").toInt()
        // Through whatever fielding holds it, dropped or not: looking only under a live
        // fielding would miss a dropped team's line-up and write the row a second time.
        val find =
            """
            SELECT e.id FROM team_roster_entry e
            JOIN team_season ts ON ts.id = e.team_season_id
            WHERE ts.team_id = ? AND ts.game = ? AND ts.season_id = ? AND e.handle = ?
            """.trimIndent()

        connection.prepareStatement("$find AND e.$ACTIVE").use { statement ->
            statement.setLong(1, teamId)
            statement.setString(2, game)
            statement.setLong(3, seasonId)
            statement.setString(4, handle)
            statement.executeQuery().use { rows ->
                if (rows.next()) {
                    val id = rows.getLong(1)
                    // The member link is not the file's to set, so it is left exactly as it is.
                    connection
                        .prepareStatement(
                            """
                            UPDATE team_roster_entry
                            SET team_role = ?, display_name = ?, sort_index = ?
                            WHERE id = ? AND NOT (team_role <=> ? AND display_name <=> ? AND sort_index <=> ?)
                            """.trimIndent(),
                        ).use { update ->
                            update.setString(1, role)
                            update.setString(2, displayName)
                            update.setInt(3, sortIndex)
                            update.setLong(4, id)
                            update.setString(5, role)
                            update.setString(6, displayName)
                            update.setInt(7, sortIndex)
                            update.executeUpdate()
                        }
                    return true
                }
            }
        }
        connection.prepareStatement("$find AND NOT e.$ACTIVE").use { statement ->
            statement.setLong(1, teamId)
            statement.setString(2, game)
            statement.setLong(3, seasonId)
            statement.setString(4, handle)
            statement.executeQuery().use { rows -> if (rows.next()) return false }
        }
        // Only now, on the path that actually writes somebody down. Fielding the team before
        // this point would field it on every run, undoing a board that dropped it.
        val fieldingId = fieldTeam(connection, teamId, game, seasonId)
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
                statement.setString(3, role)
                statement.setString(4, displayName)
                statement.setInt(5, sortIndex)
                // Attached as the place is created, which is the only moment this can be settled
                // without overruling somebody. See the note on attribution in the header.
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
        vararg args: String,
    ): Long? =
        connection.prepareStatement(sql).use { statement ->
            args.forEachIndexed { index, value -> statement.setString(index + 1, value) }
            statement.executeQuery().use { rows -> if (rows.next()) rows.getLong(1) else null }
        }

    private fun isDeleted(
        connection: Connection,
        sql: String,
        vararg args: String,
    ): Boolean = activeId(connection, sql, *args) != null

    private fun read(name: String): String = seed.read(name)

    companion object {
        private val log = LoggerFactory.getLogger(ShippedEsports::class.java)

        /** The sentinel a live row carries, as every soft-deleted table here uses it. */
        private const val ACTIVE = "deleted_at = '9999-12-31 23:59:59'"

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

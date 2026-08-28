package db.migration

import org.flywaydb.core.api.migration.BaseJavaMigration
import org.flywaydb.core.api.migration.Context
import org.slf4j.LoggerFactory
import java.sql.Connection
import java.sql.Date

/**
 * Loads the recovered esports history from the seed files.
 *
 * The history was recovered from years of page commits, and the files under `db/seed/esports`
 * are the reviewed record of it: one row per game, season, team and roster entry, in a form
 * somebody who was there can read and correct. This puts what those files say into the database.
 *
 * Repeatable rather than versioned, keyed on the files' own contents, so correcting a row is
 * an edit and a deploy rather than another migration. Running it against a database that
 * already agrees with the files changes nothing.
 *
 * Deletion outranks the files. A season, team or entry that was soft-deleted stays deleted
 * even while its row is still in the file: an admin removing something is a later decision
 * than the import, and resurrecting it on the next edit anywhere in the file would be a
 * surprise. Removing the row from the file is how it leaves for good.
 *
 * A roster entry's link to a member is not seeded. The recovered data has handles, not
 * accounts; the links that exist were matched once on import and are an admin's to maintain.
 */
@Suppress("unused", "ClassNaming")
class R__Esports_seed : BaseJavaMigration() {

    /**
     * The files are the migration. Flyway re-runs a repeatable migration when its checksum
     * moves, so hashing their contents is what makes an edit take effect.
     */
    override fun getChecksum(): Int = SEED_FILES.fold(7) { acc, name -> 31 * acc + read(name).hashCode() }

    override fun migrate(context: Context) {
        val connection = context.connection
        // The games come first: a team names one, and the database now enforces that it exists.
        val games = parse(read("games.csv"))
        games.forEach { row -> upsertGame(connection, row) }
        val seasons = parse(read("seasons.csv"))
        val teams = parse(read("teams.csv"))
        val roster = parse(read("roster.csv"))

        val seasonIds = seasons.associate { row -> row.getValue("name") to upsertSeason(connection, row) }
        val teamIds = teams.associate { row ->
            (row.getValue("game") to row.getValue("name")) to upsertTeam(connection, row)
        }

        var written = 0
        var skipped = 0
        roster.forEach { row ->
            val teamId = teamIds[row.getValue("game") to row.getValue("team")]
            val seasonId = seasonIds[row.getValue("season")]
            if (teamId == null || seasonId == null) {
                // The team or season it belongs to is deleted, so the entry has nowhere to go.
                skipped += 1
                return@forEach
            }
            if (upsertEntry(connection, teamId, seasonId, row)) written += 1 else skipped += 1
        }
        log.info(
            "[esports-seed] {} games, {} seasons, {} teams, {} roster entries applied ({} left to their deletion)",
            games.size,
            seasonIds.size,
            teamIds.size,
            written,
            skipped,
        )
    }

    /**
     * A game as the file has it: what it is called, the address its page answers to, the art it
     * carries and whether a team is still fielded in it.
     *
     * The code is the identity and is never rewritten. Unlike the rest of the seed a deleted game
     * is not left deleted: a game is what a team points at, so a row in the file is the statement
     * that the game exists.
     */
    private fun upsertGame(connection: Connection, row: Map<String, String>) {
        val code = row.getValue("game")
        val name = row.getValue("name")
        val slug = row.getValue("slug")
        val accent = row.getValue("accent").ifBlank { null }
        val mark = row.getValue("mark").ifBlank { null }
        val banner = row.getValue("banner").ifBlank { null }
        val sortIndex = row.getValue("sort_index").toInt()
        val fielded = row.getValue("fielded").toBoolean()
        val intro = row.getValue("intro").ifBlank { null }

        // Found whether or not it is deleted: a code is unique across every row, so there is no
        // second row to insert beside a deleted one. A game the file lists exists, so a deleted
        // row is brought back rather than duplicated.
        val existing = activeId(connection, "SELECT id FROM game_page WHERE game = ?", code)
        val fields = listOf<Any?>(name, slug, accent, mark, banner, sortIndex, fielded, intro)
        if (existing != null) {
            connection.prepareStatement(
                """
                UPDATE game_page
                SET name = ?, slug = ?, accent = ?, mark = ?, banner = ?, sort_index = ?, fielded = ?, intro = ?,
                    deleted_at = '9999-12-31 23:59:59'
                WHERE id = ?
                  AND NOT (name <=> ? AND slug <=> ? AND accent <=> ? AND mark <=> ? AND banner <=> ?
                           AND sort_index <=> ? AND fielded <=> ? AND intro <=> ? AND $ACTIVE)
                """.trimIndent(),
            ).use { statement ->
                fields.forEachIndexed { index, value -> statement.setObject(index + 1, value) }
                statement.setLong(fields.size + 1, existing)
                fields.forEachIndexed { index, value -> statement.setObject(index + fields.size + 2, value) }
                statement.executeUpdate()
            }
            return
        }
        connection.prepareStatement(
            """
            INSERT INTO game_page (game, name, slug, accent, mark, banner, sort_index, fielded, intro)
            VALUES (?, ?, ?, ?, ?, ?, ?, ?, ?)
            """.trimIndent(),
        ).use { statement ->
            (listOf<Any?>(code) + fields)
                .forEachIndexed { index, value -> statement.setObject(index + 1, value) }
            statement.executeUpdate()
        }
    }

    private fun upsertSeason(connection: Connection, row: Map<String, String>): Long? {
        val name = row.getValue("name")
        val existing = activeId(connection, "SELECT id FROM season WHERE name = ? AND $ACTIVE", name)
        if (existing == null && isDeleted(connection, "SELECT id FROM season WHERE name = ? AND NOT $ACTIVE", name)) {
            return null
        }
        val start = Date.valueOf(row.getValue("start_date"))
        val end = Date.valueOf(row.getValue("end_date"))
        if (existing != null) {
            connection.prepareStatement(
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

    private fun upsertTeam(connection: Connection, row: Map<String, String>): Long? {
        val game = row.getValue("game")
        val name = row.getValue("name")
        val image = row.getValue("image").ifBlank { null }
        val find = "SELECT id FROM team WHERE game = ? AND name = ?"
        val existing = activeId(connection, "$find AND $ACTIVE", game, name)
        if (existing == null && isDeleted(connection, "$find AND NOT $ACTIVE", game, name)) return null
        if (existing != null) {
            connection.prepareStatement("UPDATE team SET image = ? WHERE id = ? AND NOT (image <=> ?)").use { statement ->
                statement.setString(1, image)
                statement.setLong(2, existing)
                statement.setString(3, image)
                statement.executeUpdate()
            }
            return existing
        }
        connection.prepareStatement("INSERT INTO team (game, name, image) VALUES (?, ?, ?)").use { statement ->
            statement.setString(1, game)
            statement.setString(2, name)
            statement.setString(3, image)
            statement.executeUpdate()
        }
        return activeId(connection, "$find AND $ACTIVE", game, name)
    }

    /** True when the entry now says what the file says; false when it is left to its deletion. */
    private fun upsertEntry(connection: Connection, teamId: Long, seasonId: Long, row: Map<String, String>): Boolean {
        val handle = row.getValue("handle")
        val role = row.getValue("role")
        val displayName = row.getValue("display_name").ifBlank { null }
        val sortIndex = row.getValue("sort_index").toInt()
        val find = "SELECT id FROM team_roster_entry WHERE team_id = ? AND season_id = ? AND handle = ?"

        connection.prepareStatement("$find AND $ACTIVE").use { statement ->
            statement.setLong(1, teamId)
            statement.setLong(2, seasonId)
            statement.setString(3, handle)
            statement.executeQuery().use { rows ->
                if (rows.next()) {
                    val id = rows.getLong(1)
                    // The member link is not the file's to set, so it is left exactly as it is.
                    connection.prepareStatement(
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
        connection.prepareStatement("$find AND NOT $ACTIVE").use { statement ->
            statement.setLong(1, teamId)
            statement.setLong(2, seasonId)
            statement.setString(3, handle)
            statement.executeQuery().use { rows -> if (rows.next()) return false }
        }
        // A roster entry says the team was fielded that season, so the link is written with it.
        // Without this a seeded team would exist and show nowhere, since the pages read the
        // link rather than inferring one from the roster.
        fieldTeam(connection, teamId, seasonId)
        connection.prepareStatement(
            """
            INSERT INTO team_roster_entry (team_id, season_id, handle, team_role, display_name, sort_index)
            VALUES (?, ?, ?, ?, ?, ?)
            """.trimIndent(),
        ).use { statement ->
            statement.setLong(1, teamId)
            statement.setLong(2, seasonId)
            statement.setString(3, handle)
            statement.setString(4, role)
            statement.setString(5, displayName)
            statement.setInt(6, sortIndex)
            statement.executeUpdate()
        }
        return true
    }

    /** Records that a team was fielded in a season, unless it already says so. */
    private fun fieldTeam(connection: Connection, teamId: Long, seasonId: Long) {
        connection.prepareStatement(
            "SELECT id FROM team_season WHERE team_id = ? AND season_id = ? AND $ACTIVE",
        ).use { statement ->
            statement.setLong(1, teamId)
            statement.setLong(2, seasonId)
            statement.executeQuery().use { rows -> if (rows.next()) return }
        }
        connection.prepareStatement("INSERT INTO team_season (team_id, season_id) VALUES (?, ?)").use { statement ->
            statement.setLong(1, teamId)
            statement.setLong(2, seasonId)
            statement.executeUpdate()
        }
    }

    private fun activeId(connection: Connection, sql: String, vararg args: String): Long? =
        connection.prepareStatement(sql).use { statement ->
            args.forEachIndexed { index, value -> statement.setString(index + 1, value) }
            statement.executeQuery().use { rows -> if (rows.next()) rows.getLong(1) else null }
        }

    private fun isDeleted(connection: Connection, sql: String, vararg args: String): Boolean =
        activeId(connection, sql, *args) != null

    private fun read(name: String): String =
        R__Esports_seed::class.java.classLoader.getResourceAsStream("db/seed/esports/$name")
            ?.use { it.readBytes().toString(Charsets.UTF_8) }
            ?: error("Seed file db/seed/esports/$name is missing")

    companion object {
        private val log = LoggerFactory.getLogger(R__Esports_seed::class.java)
        private val SEED_FILES = listOf("games.csv", "seasons.csv", "teams.csv", "roster.csv")

        /** The sentinel a live row carries, as every soft-deleted table here uses it. */
        private const val ACTIVE = "deleted_at = '9999-12-31 23:59:59'"

        /**
         * Reads a comma-separated file with a header, quoting a field only where it has to.
         * A team called "BS Ohm, Sweet Ohm" is one field, not two.
         */
        fun parse(content: String): List<Map<String, String>> {
            val rows = splitRows(content).filter { row -> row.any { it.isNotBlank() } }
            if (rows.isEmpty()) return emptyList()
            val header = rows.first()
            return rows.drop(1).map { cells ->
                require(cells.size == header.size) { "Row has ${cells.size} fields, header has ${header.size}: $cells" }
                header.zip(cells).toMap()
            }
        }

        private fun splitRows(content: String): List<List<String>> {
            val rows = mutableListOf<List<String>>()
            var cells = mutableListOf<String>()
            val cell = StringBuilder()
            var quoted = false
            var index = 0
            while (index < content.length) {
                val char = content[index]
                when {
                    quoted && char == '"' && content.getOrNull(index + 1) == '"' -> { cell.append('"'); index += 1 }
                    char == '"' -> quoted = !quoted
                    !quoted && char == ',' -> { cells.add(cell.toString()); cell.clear() }
                    !quoted && (char == '\n' || char == '\r') -> {
                        if (char == '\r' && content.getOrNull(index + 1) == '\n') index += 1
                        cells.add(cell.toString()); cell.clear()
                        rows.add(cells); cells = mutableListOf()
                    }
                    else -> cell.append(char)
                }
                index += 1
            }
            if (cell.isNotEmpty() || cells.isNotEmpty()) { cells.add(cell.toString()); rows.add(cells) }
            return rows
        }
    }
}

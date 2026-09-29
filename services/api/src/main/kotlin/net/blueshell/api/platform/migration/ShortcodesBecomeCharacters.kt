package net.blueshell.api.platform.migration

import liquibase.change.custom.CustomTaskChange
import liquibase.change.custom.CustomTaskRollback
import liquibase.database.Database
import liquibase.database.jvm.JdbcConnection
import liquibase.exception.ValidationErrors
import liquibase.resource.ResourceAccessor
import org.slf4j.LoggerFactory
import java.sql.Connection

/**
 * Writes the emoji shortcodes stored in descriptions as the emoji themselves (architecture
 * ADR-010), so Discord shows them and the site needs no second name list to expand them. Each
 * value is kept as it was in `shortcode_rewrites` before it is rewritten, and a rollback writes
 * those back.
 */
class ShortcodesBecomeCharacters :
    CustomTaskChange,
    CustomTaskRollback {
    private var values = 0
    private var shortcodes = 0

    override fun execute(database: Database) {
        val connection = connectionOf(database)
        val names = Shortcodes.names()
        for ((table, column) in COLUMNS) {
            val changed = readRewritten(connection, table, column, names)
            for ((id, before, after) in changed) {
                connection.write(BACKUP, table, column, id, before)
                connection.write("UPDATE $table SET $column = ? WHERE id = ?", after.first, id)
                shortcodes += after.second
            }
            values += changed.size
            if (changed.isNotEmpty()) log.info("Rewrote shortcodes in {} values of {}.{}", changed.size, table, column)
        }
    }

    // Read whole before any is written, so no update moves under an open result set.
    private fun readRewritten(
        connection: Connection,
        table: String,
        column: String,
        names: Map<String, String>,
    ): List<Triple<Long, String, Pair<String, Int>>> =
        connection.prepareStatement("SELECT id, $column FROM $table WHERE $column LIKE '%:%:%'").use { select ->
            select.executeQuery().use { rows ->
                buildList {
                    while (rows.next()) {
                        val before = rows.getString(2) ?: continue
                        val after = Shortcodes.rewrite(before, names)
                        if (after.second > 0) add(Triple(rows.getLong(1), before, after))
                    }
                }
            }
        }

    override fun rollback(database: Database) {
        val connection = connectionOf(database)
        for ((table, column) in COLUMNS) {
            connection.write(
                "UPDATE $table SET $column = (SELECT before_text FROM shortcode_rewrites " +
                    "WHERE table_name = ? AND column_name = ? AND row_id = $table.id) " +
                    "WHERE id IN (SELECT row_id FROM shortcode_rewrites WHERE table_name = ? AND column_name = ?)",
                table,
                column,
                table,
                column,
            )
        }
    }

    override fun getConfirmationMessage(): String =
        "Wrote $shortcodes shortcodes as emoji in $values descriptions; each was kept as it was in shortcode_rewrites"

    override fun setUp() = Unit

    override fun setFileOpener(resourceAccessor: ResourceAccessor) = Unit

    override fun validate(database: Database) = ValidationErrors()

    private fun connectionOf(database: Database): Connection = (database.connection as JdbcConnection).underlyingConnection

    private fun Connection.write(
        sql: String,
        vararg values: Any,
    ) = prepareStatement(sql).use { statement ->
        values.forEachIndexed { at, value -> statement.setObject(at + 1, value) }
        statement.executeUpdate()
    }

    private companion object {
        /** Every column a description is written to, by table. */
        val COLUMNS =
            listOf(
                "events" to "description",
                "committees" to "description",
                "sponsors" to "description",
                "questions" to "label",
                "boards" to "description",
                "board_members" to "description",
                "game" to "intro",
                "game" to "competition_intro",
                "team_roster_entry" to "description",
            )
        const val BACKUP = "INSERT INTO shortcode_rewrites (table_name, column_name, row_id, before_text) VALUES (?, ?, ?, ?)"
        private val log = LoggerFactory.getLogger(ShortcodesBecomeCharacters::class.java)
    }
}

package net.blueshell.api.shared.seed

import java.sql.Connection

/**
 * The seed rows one seed has already written, by the key the files identify them with.
 *
 * The files add rows and never edit one: the database is the later record. A key found here is
 * skipped outright, so a row renamed, edited or deleted on the site stays that way. A key not
 * found here is recorded either way, whether it is written now or found already standing.
 */
class SeedLedger(
    private val connection: Connection,
    private val seed: String,
) {
    private val applied: MutableSet<String> =
        connection.prepareStatement("SELECT record_key FROM seed_applied WHERE seed = ?").use { statement ->
            statement.setString(1, seed)
            statement.executeQuery().use { rows ->
                buildSet { while (rows.next()) add(rows.getString(1)) }.toMutableSet()
            }
        }

    /** Whether the row keyed [key] is still to be written: never seen, and not [standing] already. */
    fun toWrite(
        key: String,
        standing: () -> Boolean,
    ): Boolean {
        if (key in applied) return false
        record(key)
        return !standing()
    }

    private fun record(key: String) {
        applied.add(key)
        // IGNORE: the key collates case- and accent-insensitively, so two spellings can be one row.
        connection.prepareStatement("INSERT IGNORE INTO seed_applied (seed, record_key) VALUES (?, ?)").use { statement ->
            statement.setString(1, seed)
            statement.setString(2, key)
            statement.executeUpdate()
        }
    }
}

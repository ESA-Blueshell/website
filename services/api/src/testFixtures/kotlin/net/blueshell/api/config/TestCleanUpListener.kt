package net.blueshell.api.config

import org.flywaydb.core.Flyway
import org.springframework.beans.factory.getBean
import org.springframework.beans.factory.getBeanProvider
import org.springframework.core.Ordered
import org.springframework.core.annotation.Order
import org.springframework.test.context.TestContext
import org.springframework.test.context.TestExecutionListener
import javax.sql.DataSource

/**
 * Test listener that deletes all rows from every test-schema table between tests (excluding Flyway
 * history). Hard-guards against accidental non-test schema truncation.
 *
 * The games the association knows are reference data rather than a test's own rows: a team and a
 * member's game account point at one, and the migration is what establishes the set. Wiping them
 * would leave every later test unable to write a team at all. They are therefore restored to what
 * the migration left, rather than excluded from the wipe — so a test that renames or adds a game
 * still does not leak into the next one.
 *
 * Uses DELETE (not TRUNCATE) inside an explicit transaction so that MariaDB/InnoDB only needs a
 * shared metadata lock + row-level write locks, rather than the exclusive table-level metadata lock
 * that TRUNCATE requires. Under CI's limited CPU resources the exclusive-MDL wait can exceed
 * innodb_lock_wait_timeout when the preceding test's transaction teardown is delayed, causing
 * spurious failures. DELETE integrates cleanly with InnoDB's lock-ordering protocol and benefits
 * from deadlock detection.
 */
@Order(Ordered.HIGHEST_PRECEDENCE)
class TestCleanUpListener : TestExecutionListener {

    override fun beforeTestMethod(testContext: TestContext) {
        val context = testContext.applicationContext
        val dataSource = context.getBean<DataSource>()
        ensureTestSchemaInitialized(context, dataSource)
        truncateAllUserTables(dataSource)
    }

    private fun ensureTestSchemaInitialized(context: org.springframework.context.ApplicationContext, dataSource: DataSource) {
        val hasTables = withConnection(dataSource) { conn ->
            requireTestSchema(conn)
            loadUserTables(conn).isNotEmpty()
        }
        if (hasTables) {
            return
        }

        val flyway = context.getBeanProvider<Flyway>().ifAvailable
            ?: error("Flyway bean not found in test context; cannot initialize '$TEST_SCHEMA' schema.")
        flyway.migrate()

        val initialized = withConnection(dataSource) { conn ->
            requireTestSchema(conn)
            loadUserTables(conn).isNotEmpty()
        }
        check(initialized) {
            "Flyway migration finished but '$TEST_SCHEMA' still has no application tables."
        }
    }

    private fun truncateAllUserTables(dataSource: DataSource) {
        withConnection(dataSource) { conn ->
            requireTestSchema(conn)
            val tables = loadUserTables(conn)
            if (tables.isEmpty()) {
                return@withConnection
            }
            val reference = REFERENCE_TABLES
                .filter { it.table in tables }
                .associate { it.table to snapshotOf(conn, it) }

            conn.autoCommit = false
            try {
                conn.createStatement().use { st ->
                    st.execute("SET FOREIGN_KEY_CHECKS = 0")
                    for (table in tables) {
                        st.execute("DELETE FROM `$TEST_SCHEMA`.`$table`")
                    }
                }
                // Restored with the checks still off, so the reference tables need not be listed
                // in dependency order and a self-referencing one — a file and the widths derived
                // from it — can go back in whatever order it was read.
                reference.forEach { (table, rows) -> restore(conn, table, rows) }
                conn.createStatement().use { st -> st.execute("SET FOREIGN_KEY_CHECKS = 1") }
                conn.commit()
            } catch (e: Exception) {
                runCatching { conn.rollback() }
                throw e
            } finally {
                conn.autoCommit = true
            }
        }
    }

    /**
     * What a reference table held when this run started, read once. The wipe puts it back after
     * every test, so what is captured is the migration's own rows unless a run was killed
     * mid-test — recreating the schema is the cure for that.
     */
    private fun snapshotOf(conn: java.sql.Connection, reference: Reference): Snapshot =
        snapshots.getOrPut(reference.table) {
            conn.createStatement().use { st ->
                st.executeQuery("SELECT * FROM `$TEST_SCHEMA`.`${reference.table}` WHERE ${reference.rows}").use { rs ->
                    val columns = (1..rs.metaData.columnCount).map { rs.metaData.getColumnName(it) }
                    val blanked = columns.indices.filter { columns[it] in reference.blanked }.toSet()
                    val rows = mutableListOf<List<Any?>>()
                    while (rs.next()) {
                        rows.add(columns.indices.map { if (it in blanked) null else rs.getObject(it + 1) })
                    }
                    Snapshot(columns, rows)
                }
            }
        }

    private fun restore(conn: java.sql.Connection, table: String, snapshot: Snapshot) {
        if (snapshot.rows.isEmpty()) return
        val columns = snapshot.columns.joinToString(", ") { "`$it`" }
        val holders = snapshot.columns.joinToString(", ") { "?" }
        conn.prepareStatement("INSERT INTO `$TEST_SCHEMA`.`$table` ($columns) VALUES ($holders)").use { ps ->
            for (row in snapshot.rows) {
                row.forEachIndexed { index, value -> ps.setObject(index + 1, value) }
                ps.addBatch()
            }
            ps.executeBatch()
        }
    }

    private fun requireTestSchema(conn: java.sql.Connection) {
        val currentDb = conn.createStatement().use { st ->
            st.executeQuery("SELECT DATABASE()").use { rs ->
                rs.next()
                rs.getString(1)
            }
        }
        check(currentDb == TEST_SCHEMA) {
            "Refusing to wipe non-test database. Connected to '$currentDb', expected '$TEST_SCHEMA'."
        }
    }

    private fun loadUserTables(conn: java.sql.Connection): List<String> {
        return conn.prepareStatement(
            "SELECT TABLE_NAME " +
                    "FROM INFORMATION_SCHEMA.TABLES " +
                    "WHERE TABLE_SCHEMA = ? AND TABLE_TYPE = 'BASE TABLE' " +
                    "AND TABLE_NAME NOT IN (?, ?)"
        ).use { ps ->
            ps.setString(1, TEST_SCHEMA)
            ps.setString(2, FLYWAY_V5_TABLE)
            ps.setString(3, FLYWAY_V3_TABLE)
            ps.executeQuery().use { rs ->
                val names = mutableListOf<String>()
                while (rs.next()) {
                    names.add(rs.getString(1))
                }
                names
            }
        }
    }

    private fun <T> withConnection(dataSource: DataSource, block: (java.sql.Connection) -> T): T {
        return dataSource.connection.use { conn ->
            conn.autoCommit = true
            block(conn)
        }
    }

    private data class Snapshot(val columns: List<String>, val rows: List<List<Any?>>)

    /** A table the migrations seed, and which of its rows the migrations are responsible for. */
    private data class Reference(
        val table: String,
        val rows: String,
        /**
         * Columns put back as null rather than as they were.
         *
         * A game points at the banner and the icon the start-up step stored for it, and the wipe
         * takes every file with it. Restoring the references as read would name files that are no
         * longer there, and restoring the files instead would put thirty-odd pictures into every
         * test that counts them. The game comes back without its pictures, which is what a game
         * nobody has given any looks like.
         */
        val blanked: Set<String> = emptySet(),
    )

    private companion object {
        /**
         * Rows the migration establishes that other tables point at, restored after every wipe.
         *
         * `users` and `authorities` are here for the service account, which owns the files the
         * site ships with. Wiping it would leave those records pointing at an uploader that is
         * not there, so every later test in the run fails for a reason that has nothing to do
         * with what it was testing. In order: a row in `authorities` names one in `users`.
         *
         * Only the rows the migrations left are put back — the snapshot is taken before any
         * test writes — so accounts a test creates still do not leak into the next one.
         */
        val REFERENCE_TABLES = listOf(
            Reference("game", "1 = 1", blanked = setOf("banner_file_id", "icon_file_id")),
            Reference("users", "id IN (SELECT user_id FROM authorities WHERE authority = 'SYSTEM')"),
            Reference("authorities", "authority = 'SYSTEM'"),
        )
        val snapshots = mutableMapOf<String, Snapshot>()

        const val TEST_SCHEMA = "blueshell-test"
        const val FLYWAY_V5_TABLE = "flyway_schema_history"
        const val FLYWAY_V3_TABLE = "schema_version"
    }
}

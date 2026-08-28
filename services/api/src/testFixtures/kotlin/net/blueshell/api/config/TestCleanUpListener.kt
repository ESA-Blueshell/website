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
 * `game_page` is reference data rather than test-owned rows: teams and game accounts have foreign
 * keys to it, and the seed migration defines the set. Wiping it makes every later test unable to
 * insert a team. The rows are restored to their post-migration state after each wipe rather than
 * excluded from it, so a test that renames or adds a game still cannot leak into the next one.
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
            val reference = REFERENCE_TABLES.filter { it in tables }.associateWith { snapshotOf(conn, it) }

            conn.autoCommit = false
            try {
                conn.createStatement().use { st ->
                    st.execute("SET FOREIGN_KEY_CHECKS = 0")
                    for (table in tables) {
                        st.execute("DELETE FROM `$TEST_SCHEMA`.`$table`")
                    }
                    st.execute("SET FOREIGN_KEY_CHECKS = 1")
                }
                reference.forEach { (table, rows) -> restore(conn, table, rows) }
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
     * The contents of a reference table when this run started, read once. Every wipe restores it,
     * so what is captured is the migration's own rows — unless a previous run was killed mid-test,
     * in which case recreate the schema.
     */
    private fun snapshotOf(conn: java.sql.Connection, table: String): Snapshot =
        snapshots.getOrPut(table) {
            conn.createStatement().use { st ->
                st.executeQuery("SELECT * FROM `$TEST_SCHEMA`.`$table`").use { rs ->
                    val columns = (1..rs.metaData.columnCount).map { rs.metaData.getColumnName(it) }
                    val rows = mutableListOf<List<Any?>>()
                    while (rs.next()) {
                        rows.add(columns.indices.map { rs.getObject(it + 1) })
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

    private companion object {
        /** Tables holding migration-defined rows that other tables reference. Restored after each wipe. */
        val REFERENCE_TABLES = listOf("game_page")
        val snapshots = mutableMapOf<String, Snapshot>()

        const val TEST_SCHEMA = "blueshell-test"
        const val FLYWAY_V5_TABLE = "flyway_schema_history"
        const val FLYWAY_V3_TABLE = "schema_version"
    }
}

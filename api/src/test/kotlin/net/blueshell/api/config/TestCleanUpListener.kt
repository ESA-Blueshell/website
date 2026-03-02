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
 * Test listener that truncates all test-schema tables between tests (excluding Flyway history).
 * Hard guards against accidental non-test schema truncation.
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

            conn.createStatement().use { st ->
                st.execute("SET FOREIGN_KEY_CHECKS = 0")
                for (table in tables) {
                    st.execute("TRUNCATE TABLE `$TEST_SCHEMA`.`$table`")
                }
                st.execute("SET FOREIGN_KEY_CHECKS = 1")
            }
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

    private companion object {
        const val TEST_SCHEMA = "blueshell-test"
        const val FLYWAY_V5_TABLE = "flyway_schema_history"
        const val FLYWAY_V3_TABLE = "schema_version"
    }
}

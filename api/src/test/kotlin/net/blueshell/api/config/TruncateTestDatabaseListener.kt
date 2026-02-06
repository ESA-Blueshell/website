package net.blueshell.api.config

import org.springframework.core.Ordered
import org.springframework.core.annotation.Order
import org.springframework.jdbc.datasource.DataSourceUtils
import org.springframework.test.context.TestContext
import org.springframework.test.context.TestExecutionListener
import java.sql.PreparedStatement
import javax.sql.DataSource

/**
 * Test listener that truncates all test-schema tables between tests (excluding Flyway history).
 * Hard guards against accidental non-test schema truncation.
 */
@Order(Ordered.HIGHEST_PRECEDENCE)
class TruncateTestDatabaseListener : TestExecutionListener {

    override fun beforeTestMethod(testContext: TestContext) {
        val dataSource = testContext.applicationContext.getBean(DataSource::class.java)
        val conn = DataSourceUtils.getConnection(dataSource)
        try {
            conn.createStatement().use { st ->
                conn.autoCommit = true

                val currentDb = st.executeQuery("SELECT DATABASE()").use { rs ->
                    rs.next()
                    rs.getString(1)
                }
                check(currentDb == TEST_SCHEMA) {
                    "Refusing to wipe non-test database. Connected to '$currentDb', expected '$TEST_SCHEMA'."
                }

                val tables = conn.prepareStatement(
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

                st.execute("SET FOREIGN_KEY_CHECKS = 0")
                for (table in tables) {
                    st.execute("TRUNCATE TABLE `$TEST_SCHEMA`.`$table`")
                }
                st.execute("SET FOREIGN_KEY_CHECKS = 1")
            }
        } finally {
            DataSourceUtils.releaseConnection(conn, dataSource)
        }
    }

    private companion object {
        const val TEST_SCHEMA = "blueshell-test"
        const val FLYWAY_V5_TABLE = "flyway_schema_history"
        const val FLYWAY_V3_TABLE = "schema_version"
    }
}

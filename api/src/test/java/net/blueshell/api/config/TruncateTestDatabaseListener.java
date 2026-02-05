package net.blueshell.api.config;

import org.springframework.core.Ordered;
import org.springframework.core.annotation.Order;
import org.springframework.jdbc.datasource.DataSourceUtils;
import org.springframework.test.context.TestContext;
import org.springframework.test.context.TestExecutionListener;

import javax.sql.DataSource;
import java.sql.Connection;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.Statement;
import java.util.ArrayList;
import java.util.List;

/**
 * Test listener that truncates all test-schema tables between tests (excluding Flyway history).
 * Hard guards against accidental non-test schema truncation.
 */
@Order(Ordered.HIGHEST_PRECEDENCE)
public class TruncateTestDatabaseListener implements TestExecutionListener {

    private static final String TEST_SCHEMA = "blueshell-test";
    private static final String FLYWAY_V5_TABLE = "flyway_schema_history";
    private static final String FLYWAY_V3_TABLE = "schema_version";

    @Override
    public void beforeTestMethod(TestContext testContext) throws Exception {
        DataSource dataSource = testContext.getApplicationContext().getBean(DataSource.class);
        Connection conn = DataSourceUtils.getConnection(dataSource);
        try (Statement st = conn.createStatement()) {

            conn.setAutoCommit(true);

            String currentDb;
            try (ResultSet rs = st.executeQuery("SELECT DATABASE()")) {
                rs.next();
                currentDb = rs.getString(1);
            }
            if (!TEST_SCHEMA.equals(currentDb)) {
                throw new IllegalStateException(
                        "Refusing to wipe non-test database. Connected to '" + currentDb +
                                "', expected '" + TEST_SCHEMA + "'.");
            }

            List<String> tables = new ArrayList<>();
            try (PreparedStatement ps = conn.prepareStatement(
                    "SELECT TABLE_NAME " +
                            "FROM INFORMATION_SCHEMA.TABLES " +
                            "WHERE TABLE_SCHEMA = ? AND TABLE_TYPE = 'BASE TABLE' " +
                            "AND TABLE_NAME NOT IN (?, ?)")) {
                ps.setString(1, TEST_SCHEMA);
                ps.setString(2, FLYWAY_V5_TABLE);
                ps.setString(3, FLYWAY_V3_TABLE);
                try (ResultSet rs = ps.executeQuery()) {
                    while (rs.next()) {
                        tables.add(rs.getString(1));
                    }
                }
            }

            st.execute("SET FOREIGN_KEY_CHECKS = 0");
            for (String table : tables) {
                st.execute("TRUNCATE TABLE `" + TEST_SCHEMA + "`.`" + table + "`");
            }
            st.execute("SET FOREIGN_KEY_CHECKS = 1");
        } finally {
            DataSourceUtils.releaseConnection(conn, dataSource);
        }
    }
}

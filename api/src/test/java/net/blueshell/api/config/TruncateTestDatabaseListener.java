package net.blueshell.api.config;

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

public class TruncateTestDatabaseListener implements TestExecutionListener {

    // Adjust if your exact schema name differs (keep backticks in SQL below if it contains a hyphen)
    private static final String TEST_SCHEMA = "blueshell-test";

    // Flyway table names across versions
    private static final String FLYWAY_V5_TABLE = "flyway_schema_history";
    private static final String FLYWAY_V3_TABLE = "schema_version";

    @Override
    public void beforeTestMethod(TestContext testContext) throws Exception {
        DataSource dataSource = testContext.getApplicationContext().getBean(DataSource.class);

        try (Connection conn = DataSourceUtils.getConnection(dataSource);
             Statement st = conn.createStatement()) {

            conn.setAutoCommit(true);

            // Safety guard: refuse to run unless we're connected to the test schema
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

            // Fetch all base tables in the test schema except Flyway's
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

            // Disable FK checks, truncate each table, re-enable FK checks
            st.execute("SET FOREIGN_KEY_CHECKS = 0");
            for (String table : tables) {
                // Qualify with schema so we only ever touch the test DB
                st.execute("TRUNCATE TABLE `" + TEST_SCHEMA + "`.`" + table + "`");
            }
            st.execute("SET FOREIGN_KEY_CHECKS = 1");
        }
    }
}

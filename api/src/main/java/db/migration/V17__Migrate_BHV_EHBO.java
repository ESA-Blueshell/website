package db.migration;

import org.flywaydb.core.api.migration.BaseJavaMigration;
import org.flywaydb.core.api.migration.Context;

import java.sql.Connection;
import java.sql.Statement;

public class V17__Migrate_BHV_EHBO extends BaseJavaMigration {
    public void migrate(Context context) throws Exception {
        Connection connection = context.getConnection();

        // Disable auto-commit mode
        boolean originalAutoCommit = connection.getAutoCommit();
        connection.setAutoCommit(false);

        try (Statement statement = connection.createStatement()) {

            // Step 1: Create the ehbo and bhv fields table
            String createEhboBhvColumns = "ALTER TABLE users " +
                    "ADD bhv BOOLEAN DEFAULT FALSE," +
                    "ADD ehbo BOOLEAN DEFAULT FALSE";
            statement.execute(createEhboBhvColumns);

            // Step 2: Set the ehbo values to true where the role EHBO is present for the user in the roles table
            String setEhboValues = "UPDATE users u " +
                    "SET ehbo = true " +
                    "WHERE EXISTS (SELECT 1 FROM roles r WHERE r.user_id = u.id AND r.role = 'EHBO')";
            statement.executeUpdate(setEhboValues);

            // Step 3: Set the bhv values to true where the role BHV is present for the user in the roles table
            String setBhvValues = "UPDATE users u " +
                    "SET bhv = true " +
                    "WHERE EXISTS (SELECT 1 FROM roles r WHERE r.user_id = u.id AND r.role = 'BHV')";
            statement.executeUpdate(setBhvValues);

            // Step 4: Remove the entries from the roles table for BHV and EHBO
            String removeBhvAndEhboRoles = "DELETE FROM roles " +
                    "WHERE role = 'BHV' OR role = 'EHBO'";
            statement.executeUpdate(removeBhvAndEhboRoles);

            // Step 5: Remove the entries from the authorities table for BHV and EBHO
            String removeBhvAndEhboAuths = "DELETE FROM authorities " +
                    "WHERE authority = 'BHV' OR authority = 'EHBO'";
            statement.executeUpdate(removeBhvAndEhboAuths);

            // Commit the transaction
            connection.commit();
        } catch (Exception e) {
            // Rollback transaction if any exception occurs
            connection.rollback();
            throw e;
        } finally {
            // Restore original auto-commit setting
            connection.setAutoCommit(originalAutoCommit);
        }
    }
}

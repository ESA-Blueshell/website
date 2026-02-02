package db.migration

import org.flywaydb.core.api.migration.BaseJavaMigration
import org.flywaydb.core.api.migration.Context

@Suppress("unused")
class V17__Migrate_BHV_EHBO : BaseJavaMigration() {
    @Throws(Exception::class)
    override fun migrate(context: Context) {
        val connection = context.connection

        // Disable auto-commit mode
        val originalAutoCommit = connection.autoCommit
        connection.autoCommit = false

        try {
            connection.createStatement().use { statement ->

                // Step 1: Create the ehbo and bhv fields table
                val createEhboBhvColumns = "ALTER TABLE users " +
                        "ADD bhv BOOLEAN DEFAULT FALSE," +
                        "ADD ehbo BOOLEAN DEFAULT FALSE"
                statement.execute(createEhboBhvColumns)

                // Step 2: Set the ehbo values to true where the role EHBO is present for the user in the roles table
                val setEhboValues = "UPDATE users u " +
                        "SET ehbo = true " +
                        "WHERE EXISTS (SELECT 1 FROM roles r WHERE r.user_id = u.id AND r.role = 'EHBO')"
                statement.executeUpdate(setEhboValues)

                // Step 3: Set the bhv values to true where the role BHV is present for the user in the roles table
                val setBhvValues = "UPDATE users u " +
                        "SET bhv = true " +
                        "WHERE EXISTS (SELECT 1 FROM roles r WHERE r.user_id = u.id AND r.role = 'BHV')"
                statement.executeUpdate(setBhvValues)

                // Step 4: Remove the entries from the roles table for BHV and EHBO
                val removeBhvAndEhboRoles = "DELETE FROM roles " +
                        "WHERE role = 'BHV' OR role = 'EHBO'"
                statement.executeUpdate(removeBhvAndEhboRoles)

                // Step 5: Remove the entries from the authorities table for BHV and EBHO
                val removeBhvAndEhboAuths = "DELETE FROM authorities " +
                        "WHERE authority = 'BHV' OR authority = 'EHBO'"
                statement.executeUpdate(removeBhvAndEhboAuths)

                // Commit the transaction
                connection.commit()
            }
        } catch (e: Exception) {
            // Rollback transaction if any exception occurs
            connection.rollback()
            throw e
        } finally {
            // Restore original auto-commit setting
            connection.autoCommit = originalAutoCommit
        }
    }
}

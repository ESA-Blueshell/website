package db.migration

import org.flywaydb.core.api.migration.BaseJavaMigration
import org.flywaydb.core.api.migration.Context
import java.io.IOException
import java.nio.file.Files
import java.nio.file.Paths
import java.nio.file.StandardCopyOption
import java.util.stream.Collectors

@Suppress("unused")
class V16__Migrate_Signatures : BaseJavaMigration() {
    @Throws(Exception::class)
    override fun migrate(context: Context) {
        val connection = context.connection
        val storageLocation = "uploads"
        val rootLocation = Paths.get(storageLocation).toAbsolutePath()
        // Disable auto-commit mode
        val originalAutoCommit = connection.autoCommit
        connection.autoCommit = false

        // List to hold the signature IDs
        val signatureIds: MutableList<Long?> = ArrayList<Long?>()

        // SQL query to retrieve all signature_ids
        var sql = "SELECT signature_id FROM users WHERE signature_id IS NOT NULL"

        connection.prepareStatement(sql).use { pstmt ->
            pstmt.executeQuery().use { rs ->

                // Iterate over the result set and add each signature_id to the list
                while (rs.next()) {
                    val signatureId = rs.getLong("signature_id")
                    signatureIds.add(signatureId)
                }
            }
        }
        if (!signatureIds.isEmpty()) {
            val sigIds = signatureIds.stream().map { obj: Long? -> obj.toString() }.collect(
                Collectors.joining(",", "(", ")")
            )

            sql = "SELECT name, url, id FROM pictures WHERE id IN " + sigIds
            connection.prepareStatement(sql).use { pstmt ->
                pstmt.executeQuery().use { rs ->
                    while (rs.next()) {
                        val pictureId = rs.getLong("id")
                        val url = rs.getString("url")
                        val name = rs.getString("name")

                        // Compute new paths
                        val newName = "signatures/" + name
                        val newUrl = url.replace(name, newName)

                        // Update database records
                        val updateSql = "UPDATE pictures SET name = ?, url = ? WHERE id = ?"
                        connection.prepareStatement(updateSql).use { updatePstmt ->
                            updatePstmt.setString(1, newName)
                            updatePstmt.setString(2, newUrl)
                            updatePstmt.setLong(3, pictureId)
                            updatePstmt.executeUpdate()
                        }
                        // Move the actual file
                        val sourceFile = rootLocation.resolve(name)
                        val targetFile = rootLocation.resolve(newName)

                        try {
                            // Ensure target directory exists
                            Files.createDirectories(targetFile.parent)

                            // Move the file
                            Files.move(sourceFile, targetFile, StandardCopyOption.REPLACE_EXISTING)
                        } catch (e: IOException) {
                            throw Exception("Failed to move file: " + sourceFile + " to " + targetFile, e)
                        }
                    }
                }
            }
        }

        try {
            connection.createStatement().use { statement ->

                // Step 1: Create the 'signatures' table
                val createSignaturesTable = ("CREATE TABLE signatures ("
                        + "    id BIGINT NOT NULL AUTO_INCREMENT,"
                        + "    name VARCHAR(255),"
                        + "    url VARCHAR(255),"
                        + "    created_at DATETIME,"
                        + "    user_id BIGINT,"
                        + "    date DATE,"
                        + "    city VARCHAR(255),"
                        + "    PRIMARY KEY (id),"
                        + "    FOREIGN KEY (user_id) REFERENCES users (id)"
                        + ");")
                statement.execute(createSignaturesTable)

                // Step 2: Move signatures from 'pictures' to 'signatures' table
                val insertIntoSignatures = ("INSERT INTO signatures (name, url, created_at, user_id, date, city) "
                        + "SELECT p.name, p.url, p.created_at, u.id, u.signature_date, u.signature_city "
                        + "FROM pictures p "
                        + "JOIN users u ON u.signature_id = p.id;")
                statement.executeUpdate(insertIntoSignatures)

                // Step 3: Drop the foreign key constraint on 'signature_id' in 'users' table
                val dropForeignKeyConstraint = "ALTER TABLE users DROP FOREIGN KEY fk_signature_id;"
                statement.execute(dropForeignKeyConstraint)

                // Step 4: Delete the signatures entries from the 'pictures' table
                val deleteFromPictures = ("DELETE p "
                        + "FROM pictures p "
                        + "WHERE p.id IN (SELECT signature_id FROM users);")
                statement.executeUpdate(deleteFromPictures)

                // Step 5: Drop the 'signature_id', 'signature_city', and 'signature_date' columns from 'users' table
                val alterUsersTable = ("ALTER TABLE users "
                        + "DROP COLUMN signature_id, "
                        + "DROP COLUMN signature_city, "
                        + "DROP COLUMN signature_date;")
                statement.execute(alterUsersTable)

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

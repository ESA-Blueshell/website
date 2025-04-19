package db.migration;

import org.flywaydb.core.api.migration.BaseJavaMigration;
import org.flywaydb.core.api.migration.Context;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.nio.file.StandardCopyOption;
import java.sql.Connection;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.Statement;
import java.util.ArrayList;
import java.util.List;
import java.util.stream.Collectors;

public class V16__Migrate_Signatures extends BaseJavaMigration {
    public void migrate(Context context) throws Exception {
        Connection connection = context.getConnection();
        String storageLocation = "uploads";
        Path rootLocation = Paths.get(storageLocation).toAbsolutePath();
        // Disable auto-commit mode
        boolean originalAutoCommit = connection.getAutoCommit();
        connection.setAutoCommit(false);

        // List to hold the signature IDs
        List<Long> signatureIds = new ArrayList<>();

        // SQL query to retrieve all signature_ids
        String sql = "SELECT signature_id FROM users WHERE signature_id IS NOT NULL";

        // Prepare and execute the SQL statement
        try (PreparedStatement pstmt = connection.prepareStatement(sql);
             ResultSet rs = pstmt.executeQuery()) {

            // Iterate over the result set and add each signature_id to the list
            while (rs.next()) {
                long signatureId = rs.getLong("signature_id");
                signatureIds.add(signatureId);
            }
        }

        if (!signatureIds.isEmpty()) {
            String sigIds = signatureIds.stream().map(String::valueOf).collect(
                    Collectors.joining(",", "(", ")")
            );

            sql = "SELECT name, url, id FROM pictures WHERE id IN " + sigIds;
            try (PreparedStatement pstmt = connection.prepareStatement(sql);
                 ResultSet rs = pstmt.executeQuery()) {

                while (rs.next()) {
                    long pictureId = rs.getLong("id");
                    String url = rs.getString("url");
                    String name = rs.getString("name");

                    // Compute new paths
                    String newName = "signatures/" + name;
                    String newUrl = url.replace(name, newName);

                    // Update database records
                    String updateSql = "UPDATE pictures SET name = ?, url = ? WHERE id = ?";
                    try (PreparedStatement updatePstmt = connection.prepareStatement(updateSql)) {
                        updatePstmt.setString(1, newName);
                        updatePstmt.setString(2, newUrl);
                        updatePstmt.setLong(3, pictureId);
                        updatePstmt.executeUpdate();
                    }

                    // Move the actual file
                    Path sourceFile = rootLocation.resolve(name);
                    Path targetFile = rootLocation.resolve(newName);

                    try {
                        // Ensure target directory exists
                        Files.createDirectories(targetFile.getParent());

                        // Move the file
                        Files.move(sourceFile, targetFile, StandardCopyOption.REPLACE_EXISTING);
                    } catch (IOException e) {
                        throw new Exception("Failed to move file: " + sourceFile + " to " + targetFile, e);
                    }
                }
            }
        }

        try (Statement statement = connection.createStatement()) {

            // Step 1: Create the 'signatures' table
            String createSignaturesTable = "CREATE TABLE signatures ("
                    + "    id BIGINT NOT NULL AUTO_INCREMENT,"
                    + "    name VARCHAR(255),"
                    + "    url VARCHAR(255),"
                    + "    created_at DATETIME,"
                    + "    user_id BIGINT,"
                    + "    date DATE,"
                    + "    city VARCHAR(255),"
                    + "    PRIMARY KEY (id),"
                    + "    FOREIGN KEY (user_id) REFERENCES users (id)"
                    + ");";
            statement.execute(createSignaturesTable);

            // Step 2: Move signatures from 'pictures' to 'signatures' table
            String insertIntoSignatures = "INSERT INTO signatures (name, url, created_at, user_id, date, city) "
                    + "SELECT p.name, p.url, p.created_at, u.id, u.signature_date, u.signature_city "
                    + "FROM pictures p "
                    + "JOIN users u ON u.signature_id = p.id;";
            statement.executeUpdate(insertIntoSignatures);

            // Step 3: Drop the foreign key constraint on 'signature_id' in 'users' table
            String dropForeignKeyConstraint = "ALTER TABLE users DROP FOREIGN KEY fk_signature_id;";
            statement.execute(dropForeignKeyConstraint);

            // Step 4: Delete the signatures entries from the 'pictures' table
            String deleteFromPictures = "DELETE p "
                    + "FROM pictures p "
                    + "WHERE p.id IN (SELECT signature_id FROM users);";
            statement.executeUpdate(deleteFromPictures);

            // Step 5: Drop the 'signature_id', 'signature_city', and 'signature_date' columns from 'users' table
            String alterUsersTable = "ALTER TABLE users "
                    + "DROP COLUMN signature_id, "
                    + "DROP COLUMN signature_city, "
                    + "DROP COLUMN signature_date;";
            statement.execute(alterUsersTable);

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

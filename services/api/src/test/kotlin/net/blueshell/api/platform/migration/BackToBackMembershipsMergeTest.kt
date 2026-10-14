package net.blueshell.api.platform.migration

import liquibase.database.DatabaseFactory
import liquibase.database.jvm.JdbcConnection
import liquibase.resource.ResourceAccessor
import org.assertj.core.api.Assertions.assertThat
import org.junit.jupiter.api.AfterEach
import org.junit.jupiter.api.Test
import org.mockito.kotlin.mock
import java.sql.DriverManager

class BackToBackMembershipsMergeTest {
    private val connection = DriverManager.getConnection("jdbc:h2:mem:memberships;MODE=MariaDB;DB_CLOSE_DELAY=-1")
    private val database = DatabaseFactory.getInstance().findCorrectDatabaseImplementation(JdbcConnection(connection))

    init {
        connection.createStatement().use { sql ->
            sql.execute(
                "CREATE TABLE memberships (id BIGINT PRIMARY KEY, user_id BIGINT, start_date DATE, end_date DATE, " +
                    "type VARCHAR(255), activated_on DATE, deleted_at DATETIME DEFAULT '9999-12-31 23:59:59', version BIGINT DEFAULT 0)",
            )
            sql.execute(
                "CREATE TABLE membership_types (id BIGINT AUTO_INCREMENT PRIMARY KEY, membership_id BIGINT, type VARCHAR(16), since DATE)",
            )
            sql.execute(
                "CREATE TABLE membership_rewrites (membership_id BIGINT PRIMARY KEY, end_date DATE, type VARCHAR(255), " +
                    "activated_on DATE, deleted_at DATETIME)",
            )
            sql.execute(
                "INSERT INTO memberships (id, user_id, start_date, end_date, type, activated_on) VALUES " +
                    // One person who turned alumni and then honorary by ending a membership and starting the next.
                    "(1, 10, '2020-09-01', '2022-09-01', 'REGULAR', '2020-09-01'), " +
                    "(2, 10, '2022-09-01', '2024-09-01', 'ALUMNI', '2022-09-01'), " +
                    "(3, 10, '2024-09-01', NULL, 'HONORARY', '2024-09-01'), " +
                    // A real gap between two memberships, the second still NONE.
                    "(4, 20, '2019-01-01', '2020-01-01', 'REGULAR', '2019-01-01'), " +
                    "(5, 20, '2021-01-01', NULL, 'NONE', NULL), " +
                    // Back to back without a change of type.
                    "(6, 30, '2020-01-01', '2021-01-01', 'REGULAR', NULL), " +
                    "(7, 30, '2021-01-01', NULL, 'REGULAR', '2021-02-01')",
            )
            // A deleted membership is no part of a merge, but keeps its type for a restore.
            sql.execute(
                "INSERT INTO memberships (id, user_id, start_date, end_date, type, activated_on, deleted_at) VALUES " +
                    "(8, 30, '2019-01-01', '2020-01-01', 'ALUMNI', '2019-01-01', '2024-05-05 10:00:00')",
            )
        }
    }

    @AfterEach
    fun close() {
        connection.createStatement().use { it.execute("DROP ALL OBJECTS") }
        connection.close()
    }

    private fun membership(id: Long): List<String?> =
        connection.createStatement().use { sql ->
            sql
                .executeQuery(
                    "SELECT start_date, end_date, type, activated_on, deleted_at = '9999-12-31 23:59:59' FROM memberships WHERE id = $id",
                ).use { rows ->
                    rows.next()
                    (1..5).map { rows.getString(it) }
                }
        }

    private fun typesOf(id: Long): List<String> =
        connection.createStatement().use { sql ->
            sql.executeQuery("SELECT type, since FROM membership_types WHERE membership_id = $id ORDER BY since").use { rows ->
                buildList { while (rows.next()) add("${rows.getString(1)} ${rows.getString(2)}") }
            }
        }

    @Test
    fun `back-to-back memberships become one with a type per old row, and a rollback splits them again`() {
        val before = (1L..8L).associateWith(::membership)
        val change = BackToBackMembershipsMerge().apply { setUp() }
        change.setFileOpener(mock<ResourceAccessor>())

        change.execute(database)

        assertThat(membership(1)).containsExactly("2020-09-01", null, "HONORARY", "2020-09-01", "TRUE")
        assertThat(typesOf(1)).containsExactly("REGULAR 2020-09-01", "ALUMNI 2022-09-01", "HONORARY 2024-09-01")
        assertThat(membership(2)[4]).isEqualTo("FALSE")
        assertThat(membership(3)[4]).isEqualTo("FALSE")

        assertThat(membership(4)).isEqualTo(before[4])
        assertThat(typesOf(4)).containsExactly("REGULAR 2019-01-01")
        assertThat(membership(5)).containsExactly("2021-01-01", null, "REGULAR", null, "TRUE")
        assertThat(typesOf(5)).containsExactly("REGULAR 2021-01-01")

        assertThat(membership(6)).containsExactly("2020-01-01", null, "REGULAR", "2021-02-01", "TRUE")
        assertThat(typesOf(6)).containsExactly("REGULAR 2020-01-01")
        assertThat(membership(7)[4]).isEqualTo("FALSE")

        assertThat(membership(8)).isEqualTo(before[8])
        assertThat(typesOf(8)).containsExactly("ALUMNI 2019-01-01")
        assertThat(change.confirmationMessage).isEqualTo("Merged 5 back-to-back memberships into 2; NONE became REGULAR on 1")

        change.rollback(database)

        assertThat((1L..8L).associateWith(::membership)).isEqualTo(before)
        assertThat(change.validate(database).hasErrors()).isFalse()
    }
}

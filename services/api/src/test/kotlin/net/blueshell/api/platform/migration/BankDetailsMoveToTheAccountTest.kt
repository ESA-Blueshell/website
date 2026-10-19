package net.blueshell.api.platform.migration

import liquibase.database.DatabaseFactory
import liquibase.database.jvm.JdbcConnection
import liquibase.resource.ResourceAccessor
import org.assertj.core.api.Assertions.assertThat
import org.junit.jupiter.api.AfterEach
import org.junit.jupiter.api.Test
import org.mockito.kotlin.mock
import java.sql.DriverManager

class BankDetailsMoveToTheAccountTest {
    private val connection = DriverManager.getConnection("jdbc:h2:mem:accounts;MODE=MariaDB;DB_CLOSE_DELAY=-1")
    private val database = DatabaseFactory.getInstance().findCorrectDatabaseImplementation(JdbcConnection(connection))

    init {
        connection.createStatement().use { sql ->
            sql.execute(
                "CREATE TABLE payment_details (user_id BIGINT PRIMARY KEY, mandate_iban VARCHAR(255), " +
                    "mandate_account_holder VARCHAR(512), " +
                    "mandate_iban_masked CHAR(4), mandate_reference VARCHAR(35), mandate_recorded_at TIMESTAMP, " +
                    "account_iban VARCHAR(255), account_holder VARCHAR(512), account_bic VARCHAR(11), account_iban_masked CHAR(4), " +
                    "account_version INT, account_recorded_at TIMESTAMP, mandate_account_version INT)",
            )
            sql.execute(
                "INSERT INTO payment_details (user_id, mandate_iban, mandate_account_holder, mandate_iban_masked, mandate_reference, " +
                    "mandate_recorded_at) VALUES " +
                    "(1, 'vault:v1:iban', 'vault:v1:holder', 'NL00', 'BLUESHELL-1-20260901', '2026-09-01 10:00:00'), " +
                    // A paper mandate without bank details, and a mandate whose details were wiped.
                    "(2, NULL, NULL, NULL, 'BLUESHELL-2-20261014', '2026-10-14 10:00:00'), " +
                    "(3, NULL, NULL, 'DE18', 'BLUESHELL-3-20240901', '2024-09-01 10:00:00')",
            )
        }
    }

    @AfterEach
    fun close() {
        connection.createStatement().use { it.execute("DROP ALL OBJECTS") }
        connection.close()
    }

    private fun row(userId: Long): List<String?> =
        connection.createStatement().use { sql ->
            sql
                .executeQuery(
                    "SELECT mandate_iban, mandate_account_holder, account_iban, account_holder, account_iban_masked, account_version, " +
                        "mandate_account_version, account_recorded_at FROM payment_details WHERE user_id = $userId",
                ).use { rows ->
                    rows.next()
                    (1..8).map { rows.getString(it) }
                }
        }

    @Test
    fun `a mandate's sealed details are copied to the person's bank account as they were sealed, and a rollback undoes it`() {
        val before = (1L..3L).associateWith(::row)
        val change = BankDetailsMoveToTheAccount().apply { setUp() }
        change.setFileOpener(mock<ResourceAccessor>())

        change.execute(database)

        // The mandate keeps its copy for the release before this one.
        assertThat(row(1).take(7)).containsExactly("vault:v1:iban", "vault:v1:holder", "vault:v1:iban", "vault:v1:holder", "NL00", "1", "1")
        assertThat(row(1)[7]).startsWith("2026-09-01 10:00:00")
        assertThat(row(2)).containsOnlyNulls()
        // A wiped mandate was given for an account that is gone.
        assertThat(row(3)).containsExactly(null, null, null, null, null, null, "1", null)
        assertThat(change.confirmationMessage).isEqualTo("Moved the bank details of 1 mandate to its person's bank account")

        change.rollback(database)

        assertThat((1L..3L).associateWith(::row)).isEqualTo(before)
        assertThat(change.validate(database).hasErrors()).isFalse()
    }
}

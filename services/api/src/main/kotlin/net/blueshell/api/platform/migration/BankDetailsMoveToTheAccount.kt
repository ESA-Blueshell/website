package net.blueshell.api.platform.migration

import liquibase.change.custom.CustomTaskChange
import liquibase.change.custom.CustomTaskRollback
import liquibase.database.Database
import liquibase.database.jvm.JdbcConnection
import liquibase.exception.ValidationErrors
import liquibase.resource.ResourceAccessor
import java.sql.Connection

private const val MOVE =
    "UPDATE payment_details SET account_iban = mandate_iban, account_holder = mandate_account_holder, " +
        "account_iban_masked = mandate_iban_masked, account_version = 1, account_recorded_at = mandate_recorded_at, " +
        "mandate_account_version = 1 WHERE mandate_iban IS NOT NULL"

// A mandate whose details were wiped was given for an account that is gone, which its version says.
private const val WIPED =
    "UPDATE payment_details SET mandate_account_version = 1 WHERE mandate_iban IS NULL AND mandate_iban_masked IS NOT NULL"

private const val MOVE_BACK =
    "UPDATE payment_details SET mandate_iban = COALESCE(mandate_iban, account_iban), " +
        "mandate_account_holder = COALESCE(mandate_account_holder, account_holder), account_iban = NULL, account_holder = NULL, " +
        "account_bic = NULL, account_iban_masked = NULL, account_version = NULL, account_recorded_at = NULL, mandate_account_version = NULL"

/**
 * A mandate's sealed IBAN and account holder become the person's one bank account (api ADR-041),
 * copied as they were sealed: both are sealed to the person, not to the mandate, so nothing is
 * opened on the way. The mandate's own copy stays for the release before this one to read.
 */
class BankDetailsMoveToTheAccount :
    CustomTaskChange,
    CustomTaskRollback {
    private var moved = 0

    override fun execute(database: Database) {
        val connection = connectionOf(database)
        moved = connection.prepareStatement(MOVE).use { it.executeUpdate() }
        connection.prepareStatement(WIPED).use { it.executeUpdate() }
    }

    override fun rollback(database: Database) {
        connectionOf(database).prepareStatement(MOVE_BACK).use { it.executeUpdate() }
    }

    override fun getConfirmationMessage(): String =
        "Moved the bank details of $moved mandate${if (moved == 1) "" else "s"} to its person's bank account"

    override fun setUp() = Unit

    override fun setFileOpener(resourceAccessor: ResourceAccessor) = Unit

    override fun validate(database: Database) = ValidationErrors()

    private fun connectionOf(database: Database): Connection = (database.connection as JdbcConnection).underlyingConnection
}

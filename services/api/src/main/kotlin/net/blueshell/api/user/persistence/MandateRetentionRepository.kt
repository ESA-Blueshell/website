package net.blueshell.api.user.persistence

import org.springframework.data.jpa.repository.Modifying
import org.springframework.data.jpa.repository.Query
import org.springframework.data.repository.Repository
import org.springframework.data.repository.query.Param
import java.time.Instant
import java.time.LocalDate

/** A person whose bank account no standing mandate is given for, with when it was put on file. */
interface StoppedAccountRow {
    val userId: Long

    /** The reference of the mandate given for this account, or empty where none was. */
    val reference: String
    val recordedAt: Instant
}

/** The mandates the retention rule judges, and the wipe it ends with. */
interface MandateRetentionRepository : Repository<PaymentDetails, Long> {
    @Query(
        value =
            "SELECT p.user_id AS userId, " + GIVEN_FOR + " AS reference, p.account_recorded_at AS recordedAt " +
                "FROM payment_details p WHERE p.account_iban IS NOT NULL AND " + STOPPED,
        nativeQuery = true,
    )
    fun findStopped(
        @Param("today") today: LocalDate,
    ): List<StoppedAccountRow>

    /**
     * Wipes the mandate only if it is still the one that was judged and still stopped: the row is
     * checked again in the write itself, so a mandate recorded anew since the judgement keeps its
     * bank details.
     */
    @Modifying
    @Query(
        value =
            "UPDATE payment_details p SET p.account_iban = NULL, p.account_holder = NULL, p.account_bic = NULL, " +
                "p.account_iban_masked = NULL, p.account_recorded_at = NULL, p.account_version = NULL, p.mandate_address = NULL, " +
                "p.mandate_iban = NULL, p.mandate_account_holder = NULL " +
                "WHERE p.user_id = :userId AND p.account_iban IS NOT NULL AND " + GIVEN_FOR + " = :reference AND " + STOPPED,
        nativeQuery = true,
    )
    fun wipe(
        @Param("userId") userId: Long,
        @Param("reference") reference: String,
        @Param("today") today: LocalDate,
    ): Int

    /** An erased account is no longer collected from: its standing mandate, one ending ahead included, ends on [today]. */
    @Modifying
    @Query(
        value =
            "UPDATE payment_details SET mandate_ended_on = :today " +
                "WHERE user_id = :userId AND mandate_reference IS NOT NULL AND (mandate_ended_on IS NULL OR mandate_ended_on > :today)",
        nativeQuery = true,
    )
    fun stopCollecting(
        @Param("userId") userId: Long,
        @Param("today") today: LocalDate,
    ): Int

    /** The copy the release before ADR-041 still reads; the account replacing or leaving clears it too. */
    @Modifying
    @Query(
        value = "UPDATE payment_details SET mandate_iban = NULL, mandate_account_holder = NULL WHERE user_id = :userId",
        nativeQuery = true,
    )
    fun clearMandateCopy(
        @Param("userId") userId: Long,
    ): Int

    private companion object {
        // Only a mandate given for the account on file says when that account was last collected from.
        const val GIVEN_FOR = "(CASE WHEN p.mandate_account_version = p.account_version THEN p.mandate_reference ELSE '' END)"

        // No standing mandate is given for the account on file: stopped, replaced, or never signed (api ADR-041).
        const val STOPPED =
            "NOT (p.mandate_reference IS NOT NULL AND (p.mandate_ended_on IS NULL OR p.mandate_ended_on > :today) " +
                "AND p.mandate_account_version = p.account_version)"
    }
}

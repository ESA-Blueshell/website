package net.blueshell.api.user.persistence

import org.springframework.data.jpa.repository.Modifying
import org.springframework.data.jpa.repository.Query
import org.springframework.data.repository.Repository
import org.springframework.data.repository.query.Param
import java.time.LocalDate

/** A person whose mandate still holds sealed values. */
interface StoppedMandateRow {
    val userId: Long
    val reference: String
}

/** The mandates the retention rule judges, and the wipe it ends with. */
interface MandateRetentionRepository : Repository<PaymentDetails, Long> {
    @Query(
        value =
            "SELECT p.user_id AS userId, p.mandate_reference AS reference FROM payment_details p " +
                "WHERE p.mandate_iban IS NOT NULL AND " + STOPPED,
        nativeQuery = true,
    )
    fun findStopped(
        @Param("today") today: LocalDate,
    ): List<StoppedMandateRow>

    /**
     * Wipes the mandate only if it is still the one that was judged and still stopped: the row is
     * checked again in the write itself, so a mandate recorded anew since the judgement keeps its
     * bank details.
     */
    @Modifying
    @Query(
        value =
            "UPDATE payment_details p SET p.mandate_iban = NULL, p.mandate_account_holder = NULL, p.mandate_address = NULL " +
                "WHERE p.user_id = :userId AND p.mandate_iban IS NOT NULL AND p.mandate_reference = :reference AND " + STOPPED,
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

    private companion object {
        // Ended by today: the member stopped it, its account was replaced, or their membership ended.
        const val STOPPED = "(p.mandate_ended_on IS NOT NULL AND p.mandate_ended_on <= :today)"
    }
}

package net.blueshell.api.user.persistence

import net.blueshell.api.shared.model.SoftDelete
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
     * checked again in the write itself, so a mandate recorded anew or put back on incasso since
     * the judgement keeps its bank details.
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

    /** An erased account is no longer collected from. */
    @Modifying
    @Query(value = "UPDATE payment_details SET incasso = FALSE WHERE user_id = :userId", nativeQuery = true)
    fun stopCollecting(
        @Param("userId") userId: Long,
    ): Int

    private companion object {
        // Off incasso, or every membership ended or removed. Somebody with no membership yet, such as
        // an applicant whose signup mandate waits for it, is not stopped.
        const val STOPPED =
            "(p.incasso = FALSE OR (EXISTS (SELECT 1 FROM memberships m WHERE m.user_id = p.user_id) " +
                "AND NOT EXISTS (SELECT 1 FROM memberships m WHERE m.user_id = p.user_id " +
                "AND (m.end_date IS NULL OR m.end_date > :today) AND m.deleted_at = '" + SoftDelete.LIVE + "')))"
    }
}

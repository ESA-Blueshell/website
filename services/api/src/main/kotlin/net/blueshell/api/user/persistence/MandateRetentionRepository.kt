package net.blueshell.api.user.persistence

import net.blueshell.api.shared.model.SoftDelete
import org.springframework.data.jpa.repository.Modifying
import org.springframework.data.jpa.repository.Query
import org.springframework.data.repository.Repository
import org.springframework.data.repository.query.Param
import java.time.LocalDate

/** A membership whose mandate still holds sealed values, read past soft deletion. */
interface StoppedMandateRow {
    val id: Long
    val userId: Long
    val reference: String
}

/** The mandates the retention rule judges, and the wipe it ends with. Both read and write past soft deletion. */
interface MandateRetentionRepository : Repository<Membership, Long> {
    @Query(
        value =
            "SELECT id AS id, user_id AS userId, mandate_reference AS reference FROM memberships " +
                "WHERE mandate_iban IS NOT NULL AND " + STOPPED,
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
            "UPDATE memberships SET mandate_iban = NULL, mandate_account_holder = NULL, mandate_address = NULL " +
                "WHERE id = :id AND mandate_iban IS NOT NULL AND mandate_reference = :reference AND " + STOPPED,
        nativeQuery = true,
    )
    fun wipe(
        @Param("id") id: Long,
        @Param("reference") reference: String,
        @Param("today") today: LocalDate,
    ): Int

    /** An erased account is no longer collected from: every membership of it comes off incasso. */
    @Modifying
    @Query(value = "UPDATE memberships SET incasso = FALSE WHERE user_id = :userId", nativeQuery = true)
    fun stopCollecting(
        @Param("userId") userId: Long,
    ): Int

    private companion object {
        const val STOPPED =
            "((end_date IS NOT NULL AND end_date <= :today) OR incasso = FALSE OR deleted_at <> '" + SoftDelete.LIVE + "')"
    }
}

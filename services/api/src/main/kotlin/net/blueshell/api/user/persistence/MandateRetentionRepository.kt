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
            "SELECT m.id AS id, m.user_id AS userId, m.mandate_reference AS reference " +
                "FROM memberships m JOIN users u ON u.id = m.user_id " +
                "WHERE m.mandate_iban IS NOT NULL " +
                "AND ((m.end_date IS NOT NULL AND m.end_date <= :today) OR m.incasso = FALSE " +
                "OR m.deleted_at <> '" + SoftDelete.LIVE + "' OR u.email LIKE :erased)",
        nativeQuery = true,
    )
    fun findStopped(
        @Param("today") today: LocalDate,
        @Param("erased") erased: String,
    ): List<StoppedMandateRow>

    @Modifying
    @Query(
        value =
            "UPDATE memberships SET mandate_iban = NULL, mandate_account_holder = NULL, mandate_address = NULL " +
                "WHERE id = :id AND mandate_iban IS NOT NULL",
        nativeQuery = true,
    )
    fun wipe(
        @Param("id") id: Long,
    ): Int
}

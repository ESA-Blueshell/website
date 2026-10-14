package net.blueshell.api.user.persistence

import org.springframework.data.jpa.repository.Modifying
import org.springframework.data.jpa.repository.Query
import org.springframework.data.repository.Repository
import org.springframework.data.repository.query.Param

/**
 * The sealed columns of every mandate, read and swapped for the nightly rewrap. Each swap leaves a
 * mandate somebody recorded in between alone.
 */
interface SealedMandateRepository : Repository<PaymentDetails, Long> {
    /** Every mandate's sealed values, a deleted account's too until they are wiped: what a key rotation has to move. */
    @Query(
        value =
            "SELECT id AS id, user_id AS userId, mandate_iban AS iban, mandate_account_holder AS accountHolder, " +
                "mandate_address AS address FROM payment_details WHERE mandate_iban IS NOT NULL",
        nativeQuery = true,
    )
    fun findSealedMandates(): List<SealedAccountRow>

    @Modifying
    @Query(value = "UPDATE payment_details SET mandate_iban = :sealed WHERE id = :id AND mandate_iban = :was", nativeQuery = true)
    fun swapSealedIban(
        @Param("id") id: Long,
        @Param("was") was: String,
        @Param("sealed") sealed: String,
    ): Int

    @Modifying
    @Query(
        value = "UPDATE payment_details SET mandate_account_holder = :sealed WHERE id = :id AND mandate_account_holder = :was",
        nativeQuery = true,
    )
    fun swapSealedAccountHolder(
        @Param("id") id: Long,
        @Param("was") was: String,
        @Param("sealed") sealed: String,
    ): Int

    @Modifying
    @Query(value = "UPDATE payment_details SET mandate_address = :sealed WHERE id = :id AND mandate_address = :was", nativeQuery = true)
    fun swapSealedAddress(
        @Param("id") id: Long,
        @Param("was") was: String,
        @Param("sealed") sealed: String,
    ): Int
}

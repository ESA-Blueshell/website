package net.blueshell.api.user.persistence

import org.springframework.data.jpa.repository.JpaRepository
import org.springframework.data.jpa.repository.Modifying
import org.springframework.data.jpa.repository.Query
import org.springframework.data.repository.query.Param

interface PendingMandateRepository : JpaRepository<PendingMandate, Long> {
    fun findByUserId(userId: Long): PendingMandate?

    /** Every pending mandate's sealed values: what a key rotation has to move. */
    @Query(
        value =
            "SELECT id AS id, user_id AS userId, iban AS iban, account_holder AS accountHolder, address AS address " +
                "FROM pending_mandates",
        nativeQuery = true,
    )
    fun findSealed(): List<SealedAccountRow>

    /** Swaps [was] for [sealed], and leaves a mandate somebody changed in between alone. */
    @Modifying
    @Query(value = "UPDATE pending_mandates SET iban = :sealed WHERE id = :id AND iban = :was", nativeQuery = true)
    fun swapSealedIban(
        @Param("id") id: Long,
        @Param("was") was: String,
        @Param("sealed") sealed: String,
    ): Int

    @Modifying
    @Query(value = "UPDATE pending_mandates SET account_holder = :sealed WHERE id = :id AND account_holder = :was", nativeQuery = true)
    fun swapSealedAccountHolder(
        @Param("id") id: Long,
        @Param("was") was: String,
        @Param("sealed") sealed: String,
    ): Int

    @Modifying
    @Query(value = "UPDATE pending_mandates SET address = :sealed WHERE id = :id AND address = :was", nativeQuery = true)
    fun swapSealedAddress(
        @Param("id") id: Long,
        @Param("was") was: String,
        @Param("sealed") sealed: String,
    ): Int
}

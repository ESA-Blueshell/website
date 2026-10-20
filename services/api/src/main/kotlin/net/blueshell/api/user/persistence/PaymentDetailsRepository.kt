package net.blueshell.api.user.persistence

import org.springframework.data.jpa.repository.JpaRepository
import org.springframework.data.jpa.repository.Modifying
import org.springframework.data.jpa.repository.Query
import org.springframework.data.repository.query.Param
import java.time.LocalDate

interface PaymentDetailsRepository : JpaRepository<PaymentDetails, Long> {
    fun findByUserId(userId: Long): PaymentDetails?

    fun findByUserIdIn(userIds: Collection<Long>): List<PaymentDetails>

    /**
     * Counts [on] as a use of the account on file, in one statement: several payouts to one person
     * run at once, and saving the whole row would let one undo a change made beside it. It leaves the
     * persistence context alone: clearing it would detach the declaration being marked paid.
     */
    @Modifying
    @Query(
        value =
            "UPDATE payment_details SET account_last_paid_to_on = GREATEST(COALESCE(account_last_paid_to_on, :on), :on) " +
                "WHERE user_id = :userId AND account_iban IS NOT NULL",
        nativeQuery = true,
    )
    fun recordPaidTo(
        @Param("userId") userId: Long,
        @Param("on") on: LocalDate,
    ): Int
}

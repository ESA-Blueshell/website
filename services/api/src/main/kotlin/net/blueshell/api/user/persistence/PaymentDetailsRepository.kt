package net.blueshell.api.user.persistence

import org.springframework.data.jpa.repository.JpaRepository

interface PaymentDetailsRepository : JpaRepository<PaymentDetails, Long> {
    fun findByUserId(userId: Long): PaymentDetails?

    fun findByUserIdIn(userIds: Collection<Long>): List<PaymentDetails>
}

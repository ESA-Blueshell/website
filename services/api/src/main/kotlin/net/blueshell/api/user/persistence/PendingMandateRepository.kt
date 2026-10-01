package net.blueshell.api.user.persistence

import org.springframework.data.jpa.repository.JpaRepository

interface PendingMandateRepository : JpaRepository<PendingMandate, Long> {
    fun findByUserId(userId: Long): PendingMandate?
}

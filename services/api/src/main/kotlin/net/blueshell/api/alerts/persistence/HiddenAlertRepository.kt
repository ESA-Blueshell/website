package net.blueshell.api.alerts.persistence

import org.springframework.data.jpa.repository.JpaRepository

interface HiddenAlertRepository : JpaRepository<HiddenAlert, Long> {
    fun findAllByUserId(userId: Long): List<HiddenAlert>

    fun findByUserIdAndAlertKey(
        userId: Long,
        alertKey: String,
    ): HiddenAlert?
}

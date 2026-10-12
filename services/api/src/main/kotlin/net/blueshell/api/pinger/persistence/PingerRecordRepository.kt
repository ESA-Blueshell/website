package net.blueshell.api.pinger.persistence

import org.springframework.data.jpa.repository.JpaRepository

interface PingerRecordRepository : JpaRepository<PingerRecord, Long> {
    companion object {
        const val RECORD_ID = 1L
    }
}

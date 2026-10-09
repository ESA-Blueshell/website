package net.blueshell.api.pinger.persistence

import jakarta.persistence.LockModeType
import org.springframework.data.jpa.repository.JpaRepository
import org.springframework.data.jpa.repository.Lock
import org.springframework.data.jpa.repository.Query
import org.springframework.data.repository.query.Param

interface PingerDeviceSessionRepository : JpaRepository<PingerDeviceSession, Long> {
    /**
     * The row for one (identity, device), write-locked so two reports for the same device accrue
     * one after another rather than racing a read-modify-write and losing a delta.
     */
    @Lock(LockModeType.PESSIMISTIC_WRITE)
    @Query("select d from PingerDeviceSession d where d.identity = :identity and d.deviceId = :deviceId")
    fun lockByIdentityAndDevice(
        @Param("identity") identity: String,
        @Param("deviceId") deviceId: String,
    ): PingerDeviceSession?

    fun countByIdentity(identity: String): Long

    // The least recently reporting device, evicted when an identity reaches the device cap.
    fun findFirstByIdentityOrderByUpdatedAsc(identity: String): PingerDeviceSession?
}

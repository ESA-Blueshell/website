package net.blueshell.api.pinger.persistence

import org.springframework.data.jpa.repository.JpaRepository
import org.springframework.data.jpa.repository.Modifying
import org.springframework.data.jpa.repository.Query
import org.springframework.data.repository.query.Param
import java.time.Instant

interface PingerRecordRepository : JpaRepository<PingerRecord, Long> {
    /**
     * Raises the record to [pps] only when that beats it, in one conditional write, so replicas racing
     * to set it never lower it. Returns the rows changed: 0 when the stored record is already as high.
     */
    @Modifying(clearAutomatically = true)
    @Query("update PingerRecord r set r.pps = :pps, r.setAt = :at where r.id = $RECORD_ID and r.pps < :pps")
    fun raise(
        @Param("pps") pps: Long,
        @Param("at") at: Instant,
    ): Int

    companion object {
        const val RECORD_ID = 1L
    }
}

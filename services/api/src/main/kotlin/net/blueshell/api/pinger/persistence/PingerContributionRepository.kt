package net.blueshell.api.pinger.persistence

import jakarta.persistence.LockModeType
import org.springframework.data.jpa.repository.JpaRepository
import org.springframework.data.jpa.repository.Lock
import org.springframework.data.jpa.repository.Query
import org.springframework.data.repository.query.Param

interface PingerContributionRepository : JpaRepository<PingerContribution, Long> {
    /**
     * The row for [identity], write-locked so concurrent reports for the same identity accrue one
     * after another rather than racing a read-modify-write and losing a delta.
     */
    @Lock(LockModeType.PESSIMISTIC_WRITE)
    @Query("select c from PingerContribution c where c.identity = :identity")
    fun lockByIdentity(
        @Param("identity") identity: String,
    ): PingerContribution?
}

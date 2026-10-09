package net.blueshell.api.pinger.persistence

import org.springframework.data.jpa.repository.JpaRepository

interface PingerPlacementRepository : JpaRepository<PingerPlacement, Long> {
    /** Every placement in draw order, lowest ordinal first then oldest, so the paint job reads stably. */
    fun findAllByOrderByOrdinalAscIdAsc(): List<PingerPlacement>
}

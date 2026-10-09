package net.blueshell.api.pinger.persistence

import jakarta.persistence.Column
import jakarta.persistence.Entity
import jakarta.persistence.Id
import jakarta.persistence.Table
import net.blueshell.api.shared.model.Identifiable
import java.time.Instant

/**
 * The single combined-rate record row, id 1: the top rate every online sender reached together,
 * SiteCie included, and when. The migration seeds it at zero with no [setAt], and it only ever rises.
 */
@Entity
@Table(name = "pinger_record")
class PingerRecord(
    @Id
    @Column(name = "id", nullable = false)
    override val id: Long = PingerRecordRepository.RECORD_ID,
    @Column(name = "pps", nullable = false)
    val pps: Long = 0,
    @Column(name = "set_at")
    val setAt: Instant? = null,
) : Identifiable<Long>

package net.blueshell.api.pinger.persistence

import jakarta.persistence.Column
import jakarta.persistence.Entity
import jakarta.persistence.Table
import net.blueshell.api.shared.model.AutoIdEntity
import java.time.Instant

/**
 * The final tally of pings one identity sent during the event, and its top rate.
 *
 * [identity] is "sitecie" for the SiteCie house line or "member:<id>" for a member. [memberId]
 * repeats the numeric member so the leaderboard joins to users without parsing the key, and is null
 * for SiteCie.
 */
@Entity
@Table(name = "pinger_contribution")
class PingerContribution(
    @Column(name = "identity", nullable = false, length = 64)
    val identity: String,
    @Column(name = "member_id")
    val memberId: Long?,
    @Column(name = "updated", nullable = false)
    var updated: Instant,
    @Column(name = "total_sent", nullable = false)
    var totalSent: Long = 0,
    @Column(name = "peak_pps", nullable = false)
    var peakPps: Long = 0,
    @Column(name = "peak_at")
    var peakAt: Instant? = null,
) : AutoIdEntity()

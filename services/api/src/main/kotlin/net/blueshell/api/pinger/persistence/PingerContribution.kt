package net.blueshell.api.pinger.persistence

import jakarta.persistence.Column
import jakarta.persistence.Entity
import jakarta.persistence.Table
import net.blueshell.api.shared.model.AutoIdEntity
import java.time.Instant

/**
 * The durable, monotonic tally of pings one identity has sent, and the single source of truth a
 * leaderboard reads.
 *
 * [identity] is the natural key the accrual upserts against: "sitecie" for the SiteCie house line
 * or "member:<id>" for a member. [memberId] repeats the numeric member so a leaderboard joins to
 * users without parsing the key, and is null for SiteCie. [totalSent] only ever grows, accrued from
 * the per-device deltas held in [PingerDeviceSession], so several devices of one member all count
 * into this single tally without clobbering each other. [peakPps] is the top live rate the identity
 * reached across its online devices, set at [peakAt], and only ever rises.
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

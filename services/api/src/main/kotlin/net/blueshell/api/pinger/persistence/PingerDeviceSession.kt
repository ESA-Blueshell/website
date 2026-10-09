package net.blueshell.api.pinger.persistence

import jakarta.persistence.Column
import jakarta.persistence.Entity
import jakarta.persistence.Table
import net.blueshell.api.shared.model.AutoIdEntity
import java.time.Instant

/**
 * One device's last-seen send counter, so a member running the app on several devices at once
 * accrues each device's pings exactly once.
 *
 * [identity] is the contribution's natural key ("member:<id>" or "sitecie"), [deviceId] the stable
 * per-install id the client generates once. [lastSessionSent] is that device's own cumulative
 * counter as last reported; the accrual adds the positive delta since then to the identity's single
 * [PingerContribution.totalSent]. A device whose counter drops (its own app restarted) is a fresh
 * session for that device alone and never touches another device's delta.
 */
@Entity
@Table(name = "pinger_device_session")
class PingerDeviceSession(
    @Column(name = "identity", nullable = false, length = 64)
    val identity: String,
    @Column(name = "device_id", nullable = false, length = 64)
    val deviceId: String,
    @Column(name = "updated", nullable = false)
    var updated: Instant,
    @Column(name = "last_session_sent", nullable = false)
    var lastSessionSent: Long = 0,
) : AutoIdEntity()

package net.blueshell.api.alerts.persistence

import jakarta.persistence.Column
import jakarta.persistence.Entity
import jakarta.persistence.Table
import net.blueshell.api.shared.model.AutoIdEntity
import java.time.Instant

/** An alert one person hid for themself. */
@Entity
@Table(name = "hidden_alert")
class HiddenAlert(
    @Column(name = "user_id", nullable = false)
    val userId: Long,
    @Column(name = "alert_key", nullable = false)
    val alertKey: String,
    @Column(name = "hidden_at", nullable = false)
    val hiddenAt: Instant,
) : AutoIdEntity()

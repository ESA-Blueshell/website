package net.blueshell.api.cohort.persistence

import jakarta.persistence.Column
import jakarta.persistence.Entity
import jakarta.persistence.Table
import net.blueshell.api.shared.model.AutoIdEntity
import java.time.Instant

/** One deleted target: Brevo cannot bring a list back, so the delete is recorded for people to read. */
@Entity
@Table(name = "target_deletion")
class TargetDeletion(
    @Column(name = "system", nullable = false, length = 32)
    val system: String,
    @Column(name = "external_id", nullable = false, length = 1024)
    val externalId: String,
    @Column(name = "name", nullable = false)
    val name: String,
    /** The account that deleted it; null when the api did so on its own behalf. */
    @Column(name = "deleted_by")
    val deletedBy: Long?,
    @Column(name = "deleted_at", nullable = false)
    val deletedAt: Instant,
) : AutoIdEntity()

package net.blueshell.api.domain.user.persistence.lifecycle

import jakarta.persistence.Column
import jakarta.persistence.Entity
import jakarta.persistence.Id
import jakarta.persistence.Table
import jakarta.persistence.Version
import java.time.Instant

@Entity
@Table(name = "addresses")
class AddressLifecycle(
    @Id
    var id: Long? = null,

    @Version
    @Column(name = "version", nullable = false)
    var version: Long = 0L,

    @Column(name = "deleted_at", nullable = false)
    var deletedAt: Instant = LifecycleSoftDeleteTimestamps.ACTIVE_ROW_DELETED_AT,

    @Column(name = "updated_at", nullable = false)
    var updatedAt: Instant = Instant.now()
)

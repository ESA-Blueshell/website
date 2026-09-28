package net.blueshell.api.user.persistence

import jakarta.persistence.Column
import jakarta.persistence.Entity
import jakarta.persistence.Id
import jakarta.persistence.Table
import jakarta.persistence.Version
import net.blueshell.api.shared.model.Identifiable
import net.blueshell.api.shared.model.SoftDelete
import java.time.Instant

@Entity
@Table(name = "addresses")
class AddressLifecycle(
    @Id
    override var id: Long? = null,
    @Version
    @Column(name = "version", nullable = false)
    var version: Long = 0L,
    @Column(name = "deleted_at", nullable = false)
    var deletedAt: Instant = SoftDelete.LIVE_INSTANT,
    @Column(name = "updated_at", nullable = false)
    var updatedAt: Instant = Instant.now(),
) : Identifiable<Long>

@Entity
@Table(name = "member_profiles")
class ProfileLifecycle(
    @Id
    override var id: Long? = null,
    @Version
    @Column(name = "version", nullable = false)
    var version: Long = 0L,
    @Column(name = "deleted_at", nullable = false)
    var deletedAt: Instant = SoftDelete.LIVE_INSTANT,
    @Column(name = "updated_at", nullable = false)
    var updatedAt: Instant = Instant.now(),
) : Identifiable<Long>

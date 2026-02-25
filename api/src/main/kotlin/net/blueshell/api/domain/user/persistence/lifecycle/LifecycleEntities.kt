package net.blueshell.api.domain.user.persistence.lifecycle

import jakarta.persistence.Column
import jakarta.persistence.Entity
import jakarta.persistence.Id
import jakarta.persistence.Table
import jakarta.persistence.Version
import net.blueshell.api.shared.model.Identifiable
import java.time.Instant

object SoftDeleteSentinels {
    val ACTIVE_ROW_DELETED_AT: Instant = Instant.parse("9999-12-31T23:59:59Z")
}

@Entity
@Table(name = "addresses")
class AddressLifecycle(
    @Id
    override var id: Long? = null,

    @Version
    @Column(name = "version", nullable = false)
    var version: Long = 0L,

    @Column(name = "deleted_at", nullable = false)
    var deletedAt: Instant = SoftDeleteSentinels.ACTIVE_ROW_DELETED_AT,

    @Column(name = "updated_at", nullable = false)
    var updatedAt: Instant = Instant.now()
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
    var deletedAt: Instant = SoftDeleteSentinels.ACTIVE_ROW_DELETED_AT,

    @Column(name = "updated_at", nullable = false)
    var updatedAt: Instant = Instant.now()
) : Identifiable<Long>

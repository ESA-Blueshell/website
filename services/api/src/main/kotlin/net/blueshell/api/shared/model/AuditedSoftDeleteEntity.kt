package net.blueshell.api.shared.model

import jakarta.persistence.Column
import jakarta.persistence.MappedSuperclass
import org.hibernate.annotations.ColumnDefault
import java.time.Instant
import java.time.ZoneOffset

@MappedSuperclass
abstract class AuditedSoftDeleteEntity : AuditedVersionedEntity() {
    @Column(name = "deleted_at", insertable = false, updatable = false, nullable = false)
    @ColumnDefault("'${SoftDelete.LIVE}'")
    var deletedAt: Instant? = null
        internal set

    /**
     * Returns true if this record has been soft-deleted.
     *
     * A live row carries [SoftDelete.LIVE], so `deletedAt` is never null when loaded from the
     * database. A soft delete writes the time it happened, which is in the past.
     * A threshold of year 9000 safely distinguishes the sentinel from real deletion timestamps.
     */
    val isSoftDeleted: Boolean
        get() {
            val d = deletedAt ?: return false
            return d.atZone(ZoneOffset.UTC).year < 9000
        }
}

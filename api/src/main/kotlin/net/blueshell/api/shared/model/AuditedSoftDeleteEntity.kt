package net.blueshell.api.shared.model

import jakarta.persistence.Column
import jakarta.persistence.MappedSuperclass
import org.hibernate.annotations.ColumnDefault
import java.time.Instant
import java.time.ZoneOffset

@MappedSuperclass
abstract class AuditedSoftDeleteEntity : AuditedVersionedEntity() {
    @Column(name = "deleted_at", insertable = false, updatable = false, nullable = false)
    @ColumnDefault("'9999-12-31 23:59:59'")
    var deletedAt: Instant? = null
        internal set

    /**
     * Returns true if this record has been soft-deleted.
     *
     * Active records have `deleted_at = '9999-12-31 23:59:59'` (sentinel), so `deletedAt` is
     * never null when loaded from DB. Actual soft-deletes use `NOW()`, which is in the past.
     * A threshold of year 9000 safely distinguishes the sentinel from real deletion timestamps.
     */
    val isSoftDeleted: Boolean
        get() {
            val d = deletedAt ?: return false
            return d.atZone(ZoneOffset.UTC).year < 9000
        }
}

package net.blueshell.api.shared.model

import jakarta.persistence.Column
import jakarta.persistence.MappedSuperclass
import org.hibernate.annotations.ColumnDefault
import java.time.Instant

@MappedSuperclass
abstract class SoftDeleteEntity {
    @Column(name = "deleted_at", insertable = false, updatable = false, nullable = false)
    @ColumnDefault("'9999-12-31 23:59:59'")
    var deletedAt: Instant? = null
}

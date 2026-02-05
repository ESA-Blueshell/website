package net.blueshell.api.model.base

import jakarta.persistence.*
import net.blueshell.api.model.User
import org.hibernate.annotations.ColumnDefault
import org.hibernate.annotations.CreationTimestamp
import org.hibernate.annotations.UpdateTimestamp
import org.springframework.data.annotation.CreatedBy
import org.springframework.data.annotation.LastModifiedBy
import java.time.Instant

@MappedSuperclass
abstract class AuditedVersionedEntity : VersionedEntity() {
    @CreationTimestamp
    @Column(name = "created_at", nullable = false, updatable = false)
    @ColumnDefault("CURRENT_TIMESTAMP")
    open var createdAt: Instant? = null
        protected set

    @CreatedBy
    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "created_by_id")
    open var createdBy: User? = null

    @UpdateTimestamp
    @Column(name = "updated_at", nullable = false)
    @ColumnDefault("CURRENT_TIMESTAMP")
    open var updatedAt: Instant? = null
        protected set

    @LastModifiedBy
    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "updated_by_id")
    open var updatedBy: User? = null
}

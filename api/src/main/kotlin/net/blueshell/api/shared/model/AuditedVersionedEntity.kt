package net.blueshell.api.shared.model

import jakarta.persistence.*
import net.blueshell.api.domain.user.persistence.User
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
    lateinit var createdAt: Instant
        internal set

    @CreatedBy
    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "created_by_id")
    lateinit var createdBy: User
        internal set

    @UpdateTimestamp
    @Column(name = "updated_at", nullable = false)
    @ColumnDefault("CURRENT_TIMESTAMP")
    lateinit var updatedAt: Instant
        internal set

    @LastModifiedBy
    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "updated_by_id")
    lateinit var updatedBy: User
        internal set
}

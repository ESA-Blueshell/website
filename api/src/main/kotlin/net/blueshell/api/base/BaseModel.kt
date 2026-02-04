package net.blueshell.api.base

import jakarta.persistence.*
import net.blueshell.api.model.User
import org.hibernate.Hibernate
import org.hibernate.annotations.ColumnDefault
import org.hibernate.annotations.CreationTimestamp
import org.hibernate.annotations.UpdateTimestamp
import org.springframework.data.annotation.CreatedBy
import org.springframework.data.annotation.LastModifiedBy
import java.time.Instant

@MappedSuperclass
abstract class BaseModel {
    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    public var id: Long? = null
        protected set

    @Column(name = "deleted_at", insertable = false, updatable = false, nullable = false)
    @ColumnDefault("'9999-12-31 23:59:59'")
    lateinit var deletedAt: Instant

    @CreationTimestamp
    @Column(name = "created_at", nullable = false, updatable = false)
    @ColumnDefault("CURRENT_TIMESTAMP")
    lateinit var createdAt: Instant

    @CreatedBy
    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "created_by_id")
    var createdBy: User? = null

    @UpdateTimestamp
    @Column(name = "updated_at", nullable = false)
    @ColumnDefault("CURRENT_TIMESTAMP")
    lateinit var updatedAt: Instant

    @LastModifiedBy
    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "updated_by_id")
    var updatedBy: User? = null

    @Version
    @Column(name = "version", nullable = false)
    @ColumnDefault("0")
    var version: Long = 0L

    override fun equals(other: Any?): Boolean {
        if (this === other) return true
        if (other == null) return false
        if (Hibernate.getClass<BaseModel?>(this) != Hibernate.getClass(other)) return false
        val that = other as BaseModel
        if (this.id == null || that.id == null) return false
        return this.id == that.id
    }

    override fun hashCode(): Int {
        return id?.hashCode() ?: Hibernate.getClass<BaseModel?>(this).hashCode()
    }
}

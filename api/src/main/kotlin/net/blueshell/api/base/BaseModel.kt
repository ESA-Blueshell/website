package net.blueshell.api.base

import jakarta.persistence.*
import lombok.Data
import lombok.ToString
import net.blueshell.api.model.User
import org.hibernate.Hibernate
import org.hibernate.annotations.ColumnDefault
import org.hibernate.annotations.CreationTimestamp
import org.hibernate.annotations.UpdateTimestamp
import org.springframework.data.annotation.CreatedBy
import org.springframework.data.annotation.LastModifiedBy
import java.time.Instant

@MappedSuperclass
@Data
@ToString(onlyExplicitlyIncluded = true)
abstract class BaseModel {
    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    @ToString.Include
    val id: Long? = null

    @Column(name = "deleted_at", insertable = false, updatable = false, nullable = false)
    @ColumnDefault("'9999-12-31 23:59:59'")
    @ToString.Include
    private var deletedAt: Instant? = null

    @CreationTimestamp
    @Column(name = "created_at", nullable = false, updatable = false)
    @ColumnDefault("CURRENT_TIMESTAMP")
    @ToString.Include
    private var createdAt: Instant? = null

    @CreatedBy
    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "created_by_id")
    private val createdBy: User? = null

    @UpdateTimestamp
    @Column(name = "updated_at", nullable = false)
    @ColumnDefault("CURRENT_TIMESTAMP")
    @ToString.Include
    private var updatedAt: Instant? = null

    @LastModifiedBy
    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "updated_by_id")
    private val updatedBy: User? = null

    @Version
    @Column(name = "version", nullable = false)
    @ColumnDefault("0")
    @ToString.Include
    private var version: Long? = null

    override fun equals(o: Any?): Boolean {
        if (this === o) return true
        if (o == null) return false
        if (Hibernate.getClass<BaseModel?>(this) != Hibernate.getClass<Any?>(o)) return false
        val that = o as BaseModel
        if (this.id == null || that.id == null) return false
        return this.id == that.id
    }

    override fun hashCode(): Int {
        return if (id != null) id.hashCode() else Hibernate.getClass<BaseModel?>(this).hashCode()
    }
}
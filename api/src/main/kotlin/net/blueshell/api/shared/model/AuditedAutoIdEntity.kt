package net.blueshell.api.shared.model

import jakarta.persistence.GeneratedValue
import jakarta.persistence.GenerationType
import jakarta.persistence.Id
import jakarta.persistence.MappedSuperclass
import org.hibernate.Hibernate

@MappedSuperclass
abstract class AuditedAutoIdEntity : AuditedSoftDeleteEntity(), Identifiable<Long> {
    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    override var id: Long? = null
        protected set

    override fun equals(other: Any?): Boolean {
        if (this === other) return true
        if (other == null) return false
        if (Hibernate.getClass<AuditedAutoIdEntity?>(this) != Hibernate.getClass(other)) return false
        val that = other as AuditedAutoIdEntity
        if (this.id == null || that.id == null) return false
        return this.id == that.id
    }

    override fun hashCode(): Int {
        return id?.hashCode() ?: Hibernate.getClass<AuditedAutoIdEntity?>(this).hashCode()
    }
}
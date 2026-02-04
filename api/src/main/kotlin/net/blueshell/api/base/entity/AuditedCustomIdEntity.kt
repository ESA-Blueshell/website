package net.blueshell.api.base.entity

import jakarta.persistence.Id
import jakarta.persistence.MappedSuperclass
import org.hibernate.Hibernate

@MappedSuperclass
abstract class AuditedCustomIdEntity<ID> : AuditedSoftDeleteEntity(), Identifiable<ID> {
    @Id
    override var id: ID? = null
        protected set

    override fun equals(other: Any?): Boolean {
        if (this === other) return true
        if (other == null) return false
        if (Hibernate.getClass<AuditedCustomIdEntity<*>?>(this) != Hibernate.getClass(other)) return false
        val that = other as AuditedCustomIdEntity<*>
        if (this.id == null || that.id == null) return false
        return this.id == that.id
    }

    override fun hashCode(): Int {
        return id?.hashCode() ?: Hibernate.getClass<AuditedCustomIdEntity<*>?>(this).hashCode()
    }
}

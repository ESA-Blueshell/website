package net.blueshell.api.base.entity

import jakarta.persistence.GeneratedValue
import jakarta.persistence.GenerationType
import jakarta.persistence.Id
import jakarta.persistence.MappedSuperclass
import org.hibernate.Hibernate

@MappedSuperclass
abstract class AutoIdEntity : Identifiable<Long> {
    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    override var id: Long? = null
        protected set

    override fun equals(other: Any?): Boolean {
        if (this === other) return true
        if (other == null) return false
        if (Hibernate.getClass<AutoIdEntity?>(this) != Hibernate.getClass(other)) return false
        val that = other as AutoIdEntity
        if (this.id == null || that.id == null) return false
        return this.id == that.id
    }

    override fun hashCode(): Int {
        return id?.hashCode() ?: Hibernate.getClass<AutoIdEntity?>(this).hashCode()
    }
}

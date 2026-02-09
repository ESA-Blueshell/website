package net.blueshell.api.shared.model

import jakarta.persistence.GeneratedValue
import jakarta.persistence.GenerationType
import jakarta.persistence.Id
import jakarta.persistence.MappedSuperclass
import org.hibernate.Hibernate
import kotlin.reflect.KClass

@MappedSuperclass
abstract class AuditedAutoIdEntity : AuditedSoftDeleteEntity(), Identifiable<Long> {
    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    override var id: Long? = null
        protected set

    fun assignIdForRef(value: Long) {
        id = value
    }

    companion object {
        /**
         * Creates an "id-only" instance (reference) of an entity type.
         *
         * Requires a no-arg constructor on the entity (common for JPA entities).
         */
        inline fun <reified T> asRef(id: Long): T where T : AuditedAutoIdEntity {
            val ctor = T::class.java.getDeclaredConstructor().apply { isAccessible = true }
            val instance = ctor.newInstance()
            instance.assignIdForRef(id)
            return instance
        }
    }

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

inline fun <reified T : AuditedAutoIdEntity> KClass<T>.asRef(id: Long): T =
    AuditedAutoIdEntity.asRef<T>(id)
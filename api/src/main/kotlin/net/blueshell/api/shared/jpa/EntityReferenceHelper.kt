package net.blueshell.api.shared.jpa

import jakarta.annotation.PostConstruct
import jakarta.persistence.EntityManager
import jakarta.persistence.PersistenceContext
import org.springframework.stereotype.Component

@Component
class EntityReferenceHelper(@PersistenceContext private val em: EntityManager) {

    @PostConstruct
    fun init() {
        instance = this
    }

    fun <T> ref(type: Class<T>, id: Long?): T? {
        return if (id == null) null else em.getReference(type, id)
    }

    companion object {
        lateinit var instance: EntityReferenceHelper

        /**
         * Get a JPA reference (proxy) to an entity by ID without loading it from the database.
         * This is useful for setting foreign key relationships when mapping DTOs to entities.
         *
         * The returned proxy will only trigger a database query if you access fields other than the ID.
         */
        inline fun <reified T> ref(id: Long?): T? {
            return instance.ref(T::class.java, id)
        }
    }
}

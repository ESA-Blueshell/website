package net.blueshell.api.shared.jpa

import jakarta.persistence.EntityManager
import jakarta.persistence.PersistenceContext
import org.springframework.stereotype.Component

@Component
class EntityReferenceHelper(@PersistenceContext private val em: EntityManager) {
    fun <T> ref(type: Class<T>, id: Long?): T? {
        return if (id == null) null else em.getReference(type, id)
    }
}

package net.blueshell.api.base

import jakarta.persistence.*
import net.blueshell.api.common.event.jpa.*
import org.springframework.context.ApplicationEventPublisher

class JpaListener {
    @PrePersist
    fun prePersist(entity: Any?) {
        publisher.publishEvent(PrePersistEvent(entity))
    }

    @PostPersist
    fun postPersist(entity: Any?) {
        publisher.publishEvent(PostPersistEvent(entity))
    }

    @PreUpdate
    fun preUpdate(entity: Any?) {
        publisher.publishEvent(PreUpdateEvent(entity))
    }

    @PostUpdate
    fun postUpdate(entity: Any?) {
        publisher.publishEvent(PostUpdateEvent(entity))
    }

    @PreRemove
    fun preRemove(entity: Any?) {
        publisher.publishEvent(PreRemoveEvent(entity))
    }

    @PostRemove
    fun postRemove(entity: Any?) {
        publisher.publishEvent(PostRemoveEvent(entity))
    }

    companion object {
        private lateinit var publisher: ApplicationEventPublisher

        @JvmStatic
        fun setPublisher(eventPublisher: ApplicationEventPublisher) {
            publisher = eventPublisher
        }
    }
}

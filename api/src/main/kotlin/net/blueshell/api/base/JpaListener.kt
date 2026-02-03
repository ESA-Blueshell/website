package net.blueshell.api.base

import jakarta.persistence.*
import net.blueshell.api.common.event.jpa.*
import org.springframework.context.ApplicationEventPublisher

class JpaListener {
    @PrePersist
    fun prePersist(entity: Any?) {
        if (publisher == null) return

        publisher.publishEvent(PrePersistEvent<Any?>(entity))
    }

    @PostPersist
    fun postPersist(entity: Any?) {
        if (publisher == null) return

        publisher.publishEvent(PostPersistEvent<Any?>(entity))
    }

    @PreUpdate
    fun preUpdate(entity: Any?) {
        if (publisher == null) return

        publisher.publishEvent(PreUpdateEvent<Any?>(entity))
    }

    @PostUpdate
    fun postUpdate(entity: Any?) {
        if (publisher == null) return

        publisher.publishEvent(PostUpdateEvent<Any?>(entity))
    }

    @PreRemove
    fun preRemove(entity: Any?) {
        if (publisher == null) return

        publisher.publishEvent(PreRemoveEvent<Any?>(entity))
    }

    @PostRemove
    fun postRemove(entity: Any?) {
        if (publisher == null) return

        publisher.publishEvent(PostRemoveEvent<Any?>(entity))
    }

    companion object {
        private var publisher: ApplicationEventPublisher? = null

        @JvmStatic
        fun setPublisher(eventPublisher: ApplicationEventPublisher?) {
            publisher = eventPublisher
        }
    }
}

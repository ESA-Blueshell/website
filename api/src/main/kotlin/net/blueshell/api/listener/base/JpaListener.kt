package net.blueshell.api.listener.base

import jakarta.persistence.PostPersist
import jakarta.persistence.PostRemove
import jakarta.persistence.PostUpdate
import jakarta.persistence.PrePersist
import jakarta.persistence.PreRemove
import jakarta.persistence.PreUpdate
import net.blueshell.api.common.event.jpa.PostPersistEvent
import net.blueshell.api.common.event.jpa.PostRemoveEvent
import net.blueshell.api.common.event.jpa.PostUpdateEvent
import net.blueshell.api.common.event.jpa.PrePersistEvent
import net.blueshell.api.common.event.jpa.PreRemoveEvent
import net.blueshell.api.common.event.jpa.PreUpdateEvent
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
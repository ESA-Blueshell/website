package net.blueshell.api.base;

import jakarta.persistence.*;
import lombok.Setter;
import net.blueshell.api.common.event.jpa.*;
import org.springframework.context.ApplicationEventPublisher;

public class JpaListener {

    @Setter
    private static ApplicationEventPublisher publisher;

    @PrePersist
    public void prePersist(Object entity) {
        if (publisher == null) return;

        publisher.publishEvent(new PrePersistEvent<>(entity));
    }

    @PostPersist
    public void postPersist(Object entity) {
        if (publisher == null) return;

        publisher.publishEvent(new PostPersistEvent<>(entity));
    }

    @PreUpdate
    public void preUpdate(Object entity) {
        if (publisher == null) return;

        publisher.publishEvent(new PreUpdateEvent<>(entity));
    }

    @PostUpdate
    public void postUpdate(Object entity) {
        if (publisher == null) return;

        publisher.publishEvent(new PostUpdateEvent<>(entity));
    }

    @PreRemove
    public void preRemove(Object entity) {
        if (publisher == null) return;

        publisher.publishEvent(new PreRemoveEvent<>(entity));
    }

    @PostRemove
    public void postRemove(Object entity) {
        if (publisher == null) return;

        publisher.publishEvent(new PostRemoveEvent<>(entity));
    }
}
package net.blueshell.api.base;

import jakarta.persistence.*;
import lombok.Setter;
import net.blueshell.api.common.event.*;
import org.springframework.context.ApplicationEventPublisher;

public abstract class BaseJpaListener<T> {

    @Setter
    private static ApplicationEventPublisher publisher;

    @PrePersist
    public void prePersist(T entity) {
        if (publisher == null) return;

        publisher.publishEvent(new PrePersistEvent<>(entity));
    }

    @PostPersist
    public void postPersist(T entity) {
        if (publisher == null) return;

        publisher.publishEvent(new PostPersistEvent<>(entity));
    }

    @PreUpdate
    public void preUpdate(T entity) {
        if (publisher == null) return;

        publisher.publishEvent(new PreUpdateEvent<>(entity));
    }

    @PostUpdate
    public void postUpdate(T entity) {
        if (publisher == null) return;

        publisher.publishEvent(new PostUpdateEvent<>(entity));
    }

    @PreRemove
    public void preRemove(T entity) {
        if (publisher == null) return;

        publisher.publishEvent(new PreRemoveEvent<>(entity));
    }

    @PostRemove
    public void postRemove(T entity) {
        if (publisher == null) return;

        publisher.publishEvent(new PostRemoveEvent<>(entity));
    }
}


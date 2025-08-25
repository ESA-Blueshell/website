package net.blueshell.api.model.listener;

import jakarta.persistence.*;

import lombok.Setter;
import net.blueshell.api.common.enums.Role;
import net.blueshell.api.common.event.*;
import net.blueshell.api.model.CommitteeMember;
import org.springframework.context.ApplicationEventPublisher;

public class CommitteeMemberJpaListener {

    @Setter
    private static ApplicationEventPublisher publisher;

    @PostPersist
    public void afterInsert(CommitteeMember entity) {
        if (publisher == null) return;

        publisher.publishEvent(new PostInsertEvent<>(entity));
    }

    @PostUpdate
    public void afterUpdate(CommitteeMember entity) {
        if (publisher == null) return;

        publisher.publishEvent(new PostUpdateEvent<>(entity));
    }

    @PostRemove
    public void afterDelete(CommitteeMember entity) {
        if (publisher == null) return;

        publisher.publishEvent(new PostDeleteEvent<>(entity));
    }
}
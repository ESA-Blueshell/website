package net.blueshell.api.listener.jpa;

import lombok.extern.slf4j.Slf4j;
import net.blueshell.api.common.enums.Role;
import net.blueshell.api.common.event.job.SyncContactEvent;
import net.blueshell.api.common.event.job.UserResetEmailEvent;
import net.blueshell.api.common.event.jpa.PostPersistEvent;
import net.blueshell.api.common.event.jpa.PostUpdateEvent;
import net.blueshell.api.model.User;
import net.blueshell.api.service.CommitteeMemberService;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.context.ApplicationEventPublisher;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Propagation;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.transaction.event.TransactionPhase;
import org.springframework.transaction.event.TransactionalEventListener;

@Slf4j
@Component
public class UserEventListener {

    private final ApplicationEventPublisher eventPublisher;
    private final CommitteeMemberService committeeMembers;

    @Autowired
    public UserEventListener(ApplicationEventPublisher eventPublisher, CommitteeMemberService committeeMembers) {
        this.eventPublisher = eventPublisher;
        this.committeeMembers = committeeMembers;
    }

    @TransactionalEventListener(phase = TransactionPhase.AFTER_COMMIT)
    @Transactional(propagation = Propagation.REQUIRES_NEW)
    public void postPersist(PostPersistEvent<User> evt) {
        User u = evt.getSource();
        eventPublisher.publishEvent(new UserResetEmailEvent(u.getId()));
        eventPublisher.publishEvent(new SyncContactEvent(u.getId()));
    }

    @TransactionalEventListener(phase = TransactionPhase.AFTER_COMMIT)
    @Transactional(propagation = Propagation.REQUIRES_NEW)
    public void onUpdate(PostUpdateEvent<User> evt) {
        User u = evt.getSource();
        eventPublisher.publishEvent(new SyncContactEvent(u.getId()));
        if (!u.hasRole(Role.MEMBER)) {
            u.getCommitteeMembers().forEach(committeeMembers::delete);
        }
    }
}

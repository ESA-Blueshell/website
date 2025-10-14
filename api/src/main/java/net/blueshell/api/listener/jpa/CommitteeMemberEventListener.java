package net.blueshell.api.listener.jpa;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import net.blueshell.api.common.enums.Role;
import net.blueshell.api.common.event.jpa.PostPersistEvent;
import net.blueshell.api.common.event.jpa.PostRemoveEvent;
import net.blueshell.api.common.event.jpa.PostUpdateEvent;
import net.blueshell.api.model.committee.CommitteeMember;
import net.blueshell.api.service.UserService;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Propagation;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.transaction.event.TransactionPhase;
import org.springframework.transaction.event.TransactionalEventListener;

@Slf4j
@Component
@RequiredArgsConstructor
public class CommitteeMemberEventListener {

    private final UserService users;

    @TransactionalEventListener(phase = TransactionPhase.AFTER_COMMIT)
    @Transactional(propagation = Propagation.REQUIRES_NEW)
    public void postPersist(PostPersistEvent<CommitteeMember> evt) {
        var c = evt.getSource();
        users.addRole(c.getUserId(), Role.COMMITTEE);
    }

    @TransactionalEventListener(phase = TransactionPhase.AFTER_COMMIT)
    @Transactional(propagation = Propagation.REQUIRES_NEW)
    public void postUpdate(PostUpdateEvent<CommitteeMember> evt) {
        var c = evt.getSource();
        users.addRole(c.getUserId(), Role.COMMITTEE);
    }

    @TransactionalEventListener(phase = TransactionPhase.AFTER_COMMIT)
    @Transactional(propagation = Propagation.REQUIRES_NEW)
    public void postDelete(PostRemoveEvent<CommitteeMember> evt) {
        var c = evt.getSource();
        var u = users.findById(c.getUserId());
        if (u.getCommitteeMembers().isEmpty()) {
            users.removeRole(c.getUserId(), Role.COMMITTEE);
        }
    }
}
package net.blueshell.api.event;

import lombok.extern.slf4j.Slf4j;
import net.blueshell.api.common.enums.Role;
import net.blueshell.api.common.event.PostPersistEvent;
import net.blueshell.api.common.event.PostRemoveEvent;
import net.blueshell.api.common.event.PostUpdateEvent;
import net.blueshell.api.model.committee.CommitteeMember;
import net.blueshell.api.service.UserService;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Propagation;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.transaction.event.TransactionPhase;
import org.springframework.transaction.event.TransactionalEventListener;

@Slf4j
@Component
public class CommitteeMemberEventListener {

    private final UserService users;

    public CommitteeMemberEventListener(UserService users) {
        this.users = users;
    }

    @TransactionalEventListener(phase = TransactionPhase.AFTER_COMMIT)
    @Transactional(propagation = Propagation.REQUIRES_NEW)
    public void postPersist(PostPersistEvent<CommitteeMember> evt) {
        log.info("CommitteeMemberEventListener - postPersist");
        var c = evt.getSource();
        users.addRole(c.getUserId(), Role.COMMITTEE);
    }

    @TransactionalEventListener(phase = TransactionPhase.AFTER_COMMIT)
    @Transactional(propagation = Propagation.REQUIRES_NEW)
    public void postUpdate(PostUpdateEvent<CommitteeMember> evt) {
        log.info("CommitteeMemberEventListener - postUpdate");
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
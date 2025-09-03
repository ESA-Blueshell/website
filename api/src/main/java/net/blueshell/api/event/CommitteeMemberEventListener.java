package net.blueshell.api.event;

import lombok.extern.slf4j.Slf4j;
import net.blueshell.api.common.enums.Role;
import net.blueshell.api.common.event.*;
import net.blueshell.api.model.CommitteeMember;
import net.blueshell.api.service.UserService;
import org.springframework.context.event.EventListener;
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
    public void postInsert(PostInsertEvent<CommitteeMember> evt) {
        var c = evt.getSource();
        users.addRole(c.getUser(), Role.COMMITTEE);
    }

    @TransactionalEventListener(phase = TransactionPhase.AFTER_COMMIT)
    @Transactional(propagation = Propagation.REQUIRES_NEW)
    public void postUpdate(PostUpdateEvent<CommitteeMember> evt) {
        var c = evt.getSource();
        users.addRole(c.getUser(), Role.COMMITTEE);
    }

    @TransactionalEventListener(phase = TransactionPhase.AFTER_COMMIT)
    @Transactional(propagation = Propagation.REQUIRES_NEW)
    public void postDelete(PostDeleteEvent<CommitteeMember> evt) {
        var c = evt.getSource();
        var u = c.getUser();
        if (u.getCommitteeMembers().isEmpty()) {
            users.removeRole(c.getUser(), Role.COMMITTEE);
        }
    }
}
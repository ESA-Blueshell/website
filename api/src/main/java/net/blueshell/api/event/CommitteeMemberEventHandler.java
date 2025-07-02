package net.blueshell.api.event;

import net.blueshell.api.common.enums.Role;
import net.blueshell.api.common.event.EntityCreatedEvent;
import net.blueshell.api.common.event.EntityDeletedEvent;
import net.blueshell.api.model.CommitteeMember;
import net.blueshell.api.service.UserService;
import org.springframework.stereotype.Component;
import org.springframework.transaction.event.TransactionPhase;
import org.springframework.transaction.event.TransactionalEventListener;

@Component
public class CommitteeMemberEventHandler {

    private final UserService users;

    public CommitteeMemberEventHandler(UserService users) {
        this.users = users;
    }

    @TransactionalEventListener(phase = TransactionPhase.AFTER_COMMIT)
    public void onCommitteeMemberCreated(EntityCreatedEvent<CommitteeMember> evt) {
        var c = evt.entity();
        users.addRole(c.getUser(), Role.COMMITTEE);
    }

    @TransactionalEventListener(phase = TransactionPhase.BEFORE_COMMIT)
    public void onCommitteeMemberDeleted(EntityDeletedEvent<CommitteeMember> evt) {
        var c = evt.entity();
        var u =  c.getUser();
        if (u.getCommitteeMembers().isEmpty()) {
            users.removeRole(c.getUser(), Role.COMMITTEE);
        }
    }
}


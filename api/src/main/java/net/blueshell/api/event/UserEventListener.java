package net.blueshell.api.event;

import lombok.extern.slf4j.Slf4j;
import net.blueshell.api.common.enums.Role;
import net.blueshell.api.common.event.PostUpdateEvent;
import net.blueshell.api.common.event.PrePersistEvent;
import net.blueshell.api.model.User;
import net.blueshell.api.service.CommitteeMemberService;
import net.blueshell.api.service.brevo.ContactService;
import net.blueshell.api.service.brevo.EmailService;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Propagation;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.transaction.event.TransactionPhase;
import org.springframework.transaction.event.TransactionalEventListener;

@Slf4j
@Component
public class UserEventListener {

    private final EmailService email;
    private final ContactService contacts;
    private final CommitteeMemberService committeeMembers;

    public UserEventListener(EmailService email, ContactService contacts, CommitteeMemberService committeeMembers) {
        this.email = email;
        this.contacts = contacts;
        this.committeeMembers = committeeMembers;
    }

    @TransactionalEventListener(phase = TransactionPhase.AFTER_COMMIT)
    @Transactional(propagation = Propagation.REQUIRES_NEW)
    public void onUserCreated(PrePersistEvent<User> evt) {
        User u = evt.getSource();
        contacts.sync(u);
        email.activation(u);
    }

    @TransactionalEventListener(phase = TransactionPhase.AFTER_COMMIT)
    @Transactional(propagation = Propagation.REQUIRES_NEW)
    public void onUserUpdated(PostUpdateEvent<User> evt) {
        User u = evt.getSource();
        contacts.sync(u);
        if (!u.hasRole(Role.MEMBER)) {
            u.getCommitteeMembers().forEach(committeeMembers::delete);
        }
    }
}

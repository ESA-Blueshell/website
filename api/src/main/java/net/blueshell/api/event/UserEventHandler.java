package net.blueshell.api.event;

import net.blueshell.api.common.enums.Role;
import net.blueshell.api.common.event.EntityCreatedEvent;
import net.blueshell.api.common.event.EntityUpdatedEvent;
import net.blueshell.api.model.User;
import net.blueshell.api.service.CommitteeMemberService;
import net.blueshell.api.service.brevo.ContactService;
import net.blueshell.api.service.brevo.EmailService;
import org.springframework.stereotype.Component;
import org.springframework.transaction.event.TransactionPhase;
import org.springframework.transaction.event.TransactionalEventListener;
import sendinblue.ApiException;

@Component
public class UserEventHandler {

    private final EmailService email;
    private final ContactService contacts;
    private final CommitteeMemberService committeeMembers;

    public UserEventHandler(EmailService email, ContactService contacts, CommitteeMemberService committeeMembers) {
        this.email = email;
        this.contacts = contacts;
        this.committeeMembers = committeeMembers;
    }

    /** send e-mail only if the transaction COMMITTED successfully */
    @TransactionalEventListener(phase = TransactionPhase.AFTER_COMMIT)
    public void onUserCreated(EntityCreatedEvent<User> evt) throws ApiException {
        User u = evt.entity();
        email.sendUserActivationEmail(u);
        contacts.sync(u);
    }

    @TransactionalEventListener(phase = TransactionPhase.AFTER_COMMIT)
    public void onUserUpdated(EntityUpdatedEvent<User> evt) {
        User u = evt.entity();
        if (!u.hasRole(Role.MEMBER)) {
            u.getCommitteeMembers().forEach(committeeMembers::delete);
        }
    }

    /** clean-up if the outer transaction rolls back */
    @TransactionalEventListener(phase = TransactionPhase.AFTER_ROLLBACK)
    public void onFailure(EntityCreatedEvent<User> evt) {
        User u = evt.entity();
    }
}


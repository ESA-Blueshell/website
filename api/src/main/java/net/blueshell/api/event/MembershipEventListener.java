package net.blueshell.api.event;

import lombok.extern.slf4j.Slf4j;
import net.blueshell.api.common.enums.Role;
import net.blueshell.api.common.event.PostRemoveEvent;
import net.blueshell.api.common.event.PostUpdateEvent;
import net.blueshell.api.common.event.PrePersistEvent;
import net.blueshell.api.model.Membership;
import net.blueshell.api.service.UserService;
import net.blueshell.api.service.brevo.EmailService;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Propagation;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.transaction.event.TransactionPhase;
import org.springframework.transaction.event.TransactionalEventListener;

@Slf4j
@Component
public class MembershipEventListener {

    private final EmailService email;
    private final UserService users;

    public MembershipEventListener(EmailService email, UserService users) {
        this.email = email;
        this.users = users;
    }

    @TransactionalEventListener(phase = TransactionPhase.AFTER_COMMIT)
    @Transactional(propagation = Propagation.REQUIRES_NEW)
    public void onCreate(PrePersistEvent<Membership> evt) {
        Membership m = evt.getSource();
        users.addRole(m.getUser(), Role.MEMBER);
        email.contribution(m.getUser());
    }

    @TransactionalEventListener(phase = TransactionPhase.AFTER_COMMIT)
    @Transactional(propagation = Propagation.REQUIRES_NEW)
    public void onUpdate(PostUpdateEvent<Membership> evt) {
        Membership m = evt.getSource();
        if (m.getEndDate() != null) {
            users.removeRole(m.getUser(), Role.MEMBER);
        }
    }

    @TransactionalEventListener(phase = TransactionPhase.AFTER_COMMIT)
    @Transactional(propagation = Propagation.REQUIRES_NEW)
    public void onDelete(PostRemoveEvent<Membership> evt) {
        Membership m = evt.getSource();
        users.removeRole(m.getUser(), Role.MEMBER);
    }
}

package net.blueshell.api.listener.jpa;

import lombok.extern.slf4j.Slf4j;
import net.blueshell.api.common.enums.Role;
import net.blueshell.api.common.event.jpa.PostRemoveEvent;
import net.blueshell.api.common.event.jpa.PostUpdateEvent;
import net.blueshell.api.common.event.jpa.PrePersistEvent;
import net.blueshell.api.model.Membership;
import net.blueshell.api.service.UserService;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Propagation;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.transaction.event.TransactionPhase;
import org.springframework.transaction.event.TransactionalEventListener;

@Slf4j
@Component
public class MembershipEventListener {

    private final UserService users;

    public MembershipEventListener(UserService users) {
        this.users = users;
    }

    @TransactionalEventListener(phase = TransactionPhase.AFTER_COMMIT)
    @Transactional(propagation = Propagation.REQUIRES_NEW)
    public void onCreate(PrePersistEvent<Membership> evt) {
        Membership m = evt.getSource();
        log.info("Creating membership for user {} adding role {}", m.getUserId(), Role.MEMBER);
        users.addRole(m.getUserId(), Role.MEMBER);
    }

    @TransactionalEventListener(phase = TransactionPhase.AFTER_COMMIT)
    @Transactional(propagation = Propagation.REQUIRES_NEW)
    public void onUpdate(PostUpdateEvent<Membership> evt) {
        Membership m = evt.getSource();
        if (m.getEndDate() == null) {
            log.info("Updating membership for user {} adding role {}", m.getUserId(), Role.MEMBER);
            users.addRole(m.getUserId(), Role.MEMBER);
        } else {
            log.info("Updating membership for user {} removing role {}", m.getUserId(), Role.MEMBER);
            users.removeRole(m.getUserId(), Role.MEMBER);
        }
    }

    @TransactionalEventListener(phase = TransactionPhase.AFTER_COMMIT)
    @Transactional(propagation = Propagation.REQUIRES_NEW)
    public void onDelete(PostRemoveEvent<Membership> evt) {
        Membership m = evt.getSource();
        log.info("Deleting membership for user {} removing role {}", m.getUserId(), Role.MEMBER);
        users.removeRole(m.getUserId(), Role.MEMBER);
    }
}

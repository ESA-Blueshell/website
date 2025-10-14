package net.blueshell.api.event;

import lombok.extern.slf4j.Slf4j;
import net.blueshell.api.common.enums.Role;
import net.blueshell.api.common.event.PostRemoveEvent;
import net.blueshell.api.common.event.PostUpdateEvent;
import net.blueshell.api.common.event.PrePersistEvent;
import net.blueshell.api.model.Membership;
import net.blueshell.api.service.contribution.ContributionPeriodService;
import net.blueshell.api.service.UserService;
import net.blueshell.api.service.email.EmailService;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Propagation;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.transaction.event.TransactionPhase;
import org.springframework.transaction.event.TransactionalEventListener;

@Slf4j
@Component
public class MembershipEventListener {

    private final EmailService emails;
    private final UserService users;
    private final ContributionPeriodService periods;

    public MembershipEventListener(EmailService emails, UserService users, ContributionPeriodService periods) {
        this.emails = emails;
        this.users = users;
        this.periods = periods;
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

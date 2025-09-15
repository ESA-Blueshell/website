package net.blueshell.api.event;

import lombok.extern.slf4j.Slf4j;
import net.blueshell.api.common.enums.Role;
import net.blueshell.api.common.event.PostPersistEvent;
import net.blueshell.api.common.event.PostUpdateEvent;
import net.blueshell.api.common.event.PrePersistEvent;
import net.blueshell.api.job.SyncContactJob;
import net.blueshell.api.model.User;
import net.blueshell.api.service.CommitteeMemberService;
import net.blueshell.api.service.email.EmailService;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Propagation;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.transaction.event.TransactionPhase;
import org.springframework.transaction.event.TransactionalEventListener;

@Slf4j
@Component
public class UserEventListener {

    private final EmailService email;
    private final SyncContactJob syncContactJob;
    private final CommitteeMemberService committeeMembers;

    public UserEventListener(EmailService email, SyncContactJob syncContactJob, CommitteeMemberService committeeMembers) {
        this.email = email;
        this.syncContactJob = syncContactJob;
        this.committeeMembers = committeeMembers;
    }

    @TransactionalEventListener(phase = TransactionPhase.BEFORE_COMMIT)
    public void prePersist(PrePersistEvent<User> evt) {
        User u = evt.getSource();
        syncContactJob.sync(u.getId());
    }

    @TransactionalEventListener(phase = TransactionPhase.AFTER_COMMIT)
    @Transactional(propagation = Propagation.REQUIRES_NEW)
    public void postPersist(PostPersistEvent<User> evt) {
        User u = evt.getSource();
        email.activation(u);
    }

    @TransactionalEventListener(phase = TransactionPhase.AFTER_COMMIT)
    @Transactional(propagation = Propagation.REQUIRES_NEW)
    public void onUpdate(PostUpdateEvent<User> evt) {
        User u = evt.getSource();
        syncContactJob.sync(u.getId());
        if (!u.hasRole(Role.MEMBER)) {
            u.getCommitteeMembers().forEach(committeeMembers::delete);
        }
    }
}

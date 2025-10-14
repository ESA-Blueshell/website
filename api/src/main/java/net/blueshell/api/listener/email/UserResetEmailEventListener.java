package net.blueshell.api.listener.email;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import net.blueshell.api.common.event.job.UserResetEmailEvent;
import net.blueshell.api.job.email.UserResetEmailJob;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Propagation;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.transaction.event.TransactionPhase;
import org.springframework.transaction.event.TransactionalEventListener;

@Slf4j
@Component
@RequiredArgsConstructor
public class UserResetEmailEventListener {

    private final UserResetEmailJob userResetEmailJob;

    @TransactionalEventListener(phase = TransactionPhase.AFTER_COMMIT)
    @Transactional(propagation = Propagation.REQUIRES_NEW)
    public void onReset(UserResetEmailEvent evt) {
        var userId = evt.userId();
        if (evt.userId() == null) return;

        userResetEmailJob.send(userId);
    }
}

package net.blueshell.api.listener.email;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import net.blueshell.api.common.event.job.RecoveryEmailEvent;
import net.blueshell.api.job.email.RecoveryEmailJob;
import org.springframework.context.event.EventListener;
import org.springframework.stereotype.Component;

@Slf4j
@Component
@RequiredArgsConstructor
public class RecoveryEmailEventListener {

    private final RecoveryEmailJob recoveryEmailJob;

    @EventListener
    public void onReset(RecoveryEmailEvent evt) {
        var userId = evt.userId();
        if (evt.userId() == null) return;

        recoveryEmailJob.send(userId, evt.token(), evt.resetType());
    }
}

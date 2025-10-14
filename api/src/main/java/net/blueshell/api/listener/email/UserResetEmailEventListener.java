package net.blueshell.api.listener.email;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import net.blueshell.api.common.event.job.UserResetEmailEvent;
import net.blueshell.api.job.email.UserResetEmailJob;
import org.springframework.context.event.EventListener;
import org.springframework.stereotype.Component;

@Slf4j
@Component
@RequiredArgsConstructor
public class UserResetEmailEventListener {

    private final UserResetEmailJob userResetEmailJob;

    @EventListener
    public void onReset(UserResetEmailEvent evt) {
        var userId = evt.userId();
        if (evt.userId() == null) return;

        userResetEmailJob.send(userId);
    }
}

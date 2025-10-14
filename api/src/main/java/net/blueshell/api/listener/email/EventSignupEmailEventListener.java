package net.blueshell.api.listener.email;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import net.blueshell.api.common.event.job.EventSignupEmailEvent;
import net.blueshell.api.job.email.EventSignupEmailJob;
import org.springframework.context.event.EventListener;
import org.springframework.stereotype.Component;

@Slf4j
@Component
@RequiredArgsConstructor
public class EventSignupEmailEventListener {

    private final EventSignupEmailJob job;

    @EventListener
    public void onSend(EventSignupEmailEvent evt) {
        Long id = evt.eventSignUpId();
        if (id == null) return;
        job.send(id);
    }
}
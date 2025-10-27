package net.blueshell.api.listener.email;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import net.blueshell.api.common.event.job.ContributionReminderEmailEvent;
import net.blueshell.api.job.email.ContributionReminderEmailJob;
import org.springframework.context.event.EventListener;
import org.springframework.stereotype.Component;

@Slf4j
@Component
@RequiredArgsConstructor
public class ContributionReminderEmailEventListener {

    private final ContributionReminderEmailJob job;

    @EventListener
    public void onSend(ContributionReminderEmailEvent evt) {
        Long reminderId = evt.reminderId();
        if (reminderId == null) return;
        job.send(reminderId);
    }
}
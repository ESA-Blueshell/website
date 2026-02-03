package net.blueshell.api.listener.email

import net.blueshell.api.common.event.job.ContributionReminderEmailEvent
import net.blueshell.api.job.email.ContributionReminderEmailJob
import org.springframework.context.event.EventListener
import org.springframework.stereotype.Component

@Component
class ContributionReminderEmailEventListener(
    private val job: ContributionReminderEmailJob
) {
    @EventListener
    fun onSend(evt: ContributionReminderEmailEvent) {
        val reminderId = evt.reminderId
        if (reminderId == null) return
        job.send(reminderId)
    }
}

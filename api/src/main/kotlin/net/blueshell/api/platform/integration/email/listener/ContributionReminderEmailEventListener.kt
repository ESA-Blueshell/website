package net.blueshell.api.platform.integration.email.listener

import net.blueshell.api.platform.integration.event.job.ContributionReminderEmailEvent
import net.blueshell.api.platform.integration.email.job.ContributionReminderEmailJob
import org.springframework.context.event.EventListener
import org.springframework.stereotype.Component

@Component
class ContributionReminderEmailEventListener(
    private val job: ContributionReminderEmailJob
) {
    @EventListener
    fun onSend(evt: ContributionReminderEmailEvent) {
        val userId = evt.userId ?: return
        val contributionPeriodId = evt.contributionPeriodId ?: return
        job.send(userId, contributionPeriodId)
    }
}

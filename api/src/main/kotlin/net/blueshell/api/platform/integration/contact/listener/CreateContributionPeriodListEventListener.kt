package net.blueshell.api.platform.integration.contact.listener

import net.blueshell.api.platform.integration.event.job.CreateContributionPeriodListEvent
import net.blueshell.api.platform.integration.contact.job.CreateContributionPeriodListJob
import org.springframework.context.event.EventListener
import org.springframework.stereotype.Component

@Component
class CreateContributionPeriodListEventListener(
    private val job: CreateContributionPeriodListJob
) {
    @EventListener
    fun onCreate(evt: CreateContributionPeriodListEvent) {
        val periodId = evt.periodId ?: return
        job.createList(periodId)
    }
}

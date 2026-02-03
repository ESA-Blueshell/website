package net.blueshell.api.listener.contact

import net.blueshell.api.common.event.job.CreateContributionPeriodListEvent
import net.blueshell.api.job.contact.CreateContributionPeriodListJob
import org.springframework.context.event.EventListener
import org.springframework.stereotype.Component

@Component
class CreateContributionPeriodListEventListener(
    private val job: CreateContributionPeriodListJob
) {
    @EventListener
    fun onCreate(evt: CreateContributionPeriodListEvent) {
        val periodId = evt.periodId
        if (periodId == null) return
        job.createList(periodId)
    }
}

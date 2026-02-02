package net.blueshell.api.listener.contact

import lombok.RequiredArgsConstructor
import lombok.extern.slf4j.Slf4j
import net.blueshell.api.common.event.job.CreateContributionPeriodListEvent
import net.blueshell.api.job.contact.CreateContributionPeriodListJob
import org.springframework.context.event.EventListener
import org.springframework.stereotype.Component

@Slf4j
@Component
@RequiredArgsConstructor
class CreateContributionPeriodListEventListener {
    private val job: CreateContributionPeriodListJob? = null

    @EventListener
    fun onCreate(evt: CreateContributionPeriodListEvent) {
        val periodId = evt.periodId
        if (periodId == null) return
        job!!.createList(periodId)
    }
}
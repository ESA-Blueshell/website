package net.blueshell.api.contribution.application.listener

import net.blueshell.api.contribution.application.event.ContributionPeriodChangedEvent
import net.blueshell.api.contribution.application.ContributionPeriodService
import net.blueshell.api.platform.integration.contact.job.CreateContributionPeriodListJobHandler
import net.blueshell.api.platform.integration.contact.job.CreateContributionPeriodListPayload
import net.blueshell.api.platform.integration.queue.JobDispatcher
import org.springframework.context.event.EventListener
import org.springframework.stereotype.Component
import org.springframework.transaction.annotation.Propagation
import org.springframework.transaction.annotation.Transactional

@Component
class ContributionPeriodEventListener(
    private val jobDispatcher: JobDispatcher,
    private val periods: ContributionPeriodService
) {
    @EventListener
    @Transactional(propagation = Propagation.REQUIRES_NEW)
    fun onChange(evt: ContributionPeriodChangedEvent) {
        val c = periods.findById(evt.periodId)
        if (c.listId != null) return
        jobDispatcher.enqueue(
            CreateContributionPeriodListJobHandler.JOB_TYPE,
            CreateContributionPeriodListPayload(c.id!!)
        )
    }
}

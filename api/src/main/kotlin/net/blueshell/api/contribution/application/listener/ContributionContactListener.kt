package net.blueshell.api.contribution.application.listener

import net.blueshell.api.contribution.application.ContributionPeriodService
import net.blueshell.api.contribution.application.event.ContributionChange
import net.blueshell.api.contribution.application.event.ContributionChanged
import net.blueshell.api.contribution.application.event.ContributionPeriodChanged
import net.blueshell.api.platform.integration.contact.job.AddContactToListJob
import net.blueshell.api.platform.integration.contact.job.CreateContributionPeriodListJob
import net.blueshell.api.platform.integration.contact.job.RemoveContactFromListJob
import net.blueshell.api.platform.integration.queue.JobDispatcher
import org.springframework.context.event.EventListener
import org.springframework.stereotype.Component
import org.springframework.transaction.annotation.Propagation
import org.springframework.transaction.annotation.Transactional

@Component
class ContributionContactListener(
    private val jobDispatcher: JobDispatcher,
    private val periods: ContributionPeriodService
) {
    @EventListener
    @Transactional(propagation = Propagation.REQUIRES_NEW)
    fun onContributionChange(evt: ContributionChanged) {
        when (evt.changeType) {
            ContributionChange.CREATED,
            ContributionChange.UPDATED -> jobDispatcher.enqueue(
                AddContactToListJob.TYPE,
                AddContactToListJob.Payload(evt.userId, evt.periodId)
            )
            ContributionChange.DELETED -> jobDispatcher.enqueue(
                RemoveContactFromListJob.TYPE,
                RemoveContactFromListJob.Payload(evt.userId, evt.periodId)
            )
        }
    }

    @EventListener
    @Transactional(propagation = Propagation.REQUIRES_NEW)
    fun onPeriodChange(evt: ContributionPeriodChanged) {
        val period = periods.findById(evt.periodId)
        if (period.listId != null) return
        jobDispatcher.enqueue(
            CreateContributionPeriodListJob.TYPE,
            CreateContributionPeriodListJob.Payload(period.id!!)
        )
    }
}

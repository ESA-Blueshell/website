package net.blueshell.api.domain.contribution.application.listener

import net.blueshell.api.domain.contribution.application.ContributionPeriodService
import net.blueshell.api.domain.contribution.application.event.ContributionChange
import net.blueshell.api.domain.contribution.application.event.ContributionChanged
import net.blueshell.api.domain.contribution.application.event.ContributionPeriodChanged
import net.blueshell.api.shared.job.ContactJobs
import net.blueshell.api.shared.job.JobQueue
import org.springframework.context.event.EventListener
import org.springframework.stereotype.Component
import org.springframework.transaction.annotation.Propagation
import org.springframework.transaction.annotation.Transactional

@Component
class ContributionContactListener(
    private val jobDispatcher: JobQueue,
    private val periods: ContributionPeriodService
) {
    @EventListener
    @Transactional(propagation = Propagation.REQUIRES_NEW)
    fun onContributionChange(evt: ContributionChanged) {
        when (evt.changeType) {
            ContributionChange.CREATED,
            ContributionChange.UPDATED -> jobDispatcher.enqueue(
                ContactJobs.AddToList,
                ContactJobs.AddToListPayload(evt.userId, evt.periodId)
            )

            ContributionChange.DELETED -> jobDispatcher.enqueue(
                ContactJobs.RemoveFromList,
                ContactJobs.RemoveFromListPayload(evt.userId, evt.periodId)
            )
        }
    }

    @EventListener
    @Transactional(propagation = Propagation.REQUIRES_NEW)
    fun onPeriodChange(evt: ContributionPeriodChanged) {
        val period = periods.findById(evt.periodId)
        if (period.listId != null) return
        jobDispatcher.enqueue(
            ContactJobs.CreateContributionPeriodList,
            ContactJobs.CreateContributionPeriodListPayload(period.id!!)
        )
    }
}

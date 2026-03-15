package net.blueshell.api.domain.contribution.application.listener

import net.blueshell.api.domain.contribution.application.event.ContributionChanged
import net.blueshell.api.shared.job.ContactJobs
import net.blueshell.api.shared.job.TrackedJobDispatcher
import org.springframework.context.event.EventListener
import org.springframework.stereotype.Component
import org.springframework.transaction.annotation.Propagation
import org.springframework.transaction.annotation.Transactional

@Component
class ContributionContactListener(
    private val jobs: TrackedJobDispatcher
) {
    @EventListener
    @Transactional(propagation = Propagation.REQUIRES_NEW)
    fun onContributionChange(evt: ContributionChanged) {
        jobs.enqueueFromActor(
            ContactJobs.ProcessListMembership,
            ContactJobs.ProcessListMembershipPayload(evt.userId, evt.periodId),
            evt
        )
    }
}

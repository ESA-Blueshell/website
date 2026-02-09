package net.blueshell.api.contribution.application.listener

import net.blueshell.api.contribution.application.event.ContributionChangeType
import net.blueshell.api.contribution.application.event.ContributionChangedEvent
import net.blueshell.api.platform.integration.contact.job.AddContactToListJobHandler
import net.blueshell.api.platform.integration.contact.job.AddContactToListPayload
import net.blueshell.api.platform.integration.contact.job.RemoveContactFromListJobHandler
import net.blueshell.api.platform.integration.contact.job.RemoveContactFromListPayload
import net.blueshell.api.platform.integration.queue.JobDispatcher
import org.springframework.context.event.EventListener
import org.springframework.stereotype.Component
import org.springframework.transaction.annotation.Propagation
import org.springframework.transaction.annotation.Transactional

@Component
class ContributionEventListener(
    private val jobDispatcher: JobDispatcher
) {
    @EventListener
    @Transactional(propagation = Propagation.REQUIRES_NEW)
    fun onChange(evt: ContributionChangedEvent) {
        when (evt.changeType) {
            ContributionChangeType.CREATED,
            ContributionChangeType.UPDATED -> jobDispatcher.enqueue(
                AddContactToListJobHandler.JOB_TYPE,
                AddContactToListPayload(evt.userId, evt.periodId)
            )
            ContributionChangeType.DELETED -> jobDispatcher.enqueue(
                RemoveContactFromListJobHandler.JOB_TYPE,
                RemoveContactFromListPayload(evt.userId, evt.periodId)
            )
        }
    }
}

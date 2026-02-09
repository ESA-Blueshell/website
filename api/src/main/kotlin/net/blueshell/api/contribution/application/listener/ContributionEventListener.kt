package net.blueshell.api.contribution.application.listener

import net.blueshell.api.contribution.application.event.ContributionChangeType
import net.blueshell.api.contribution.application.event.ContributionChangedEvent
import net.blueshell.api.platform.integration.event.job.AddContactToListEvent
import net.blueshell.api.platform.integration.event.job.RemoveContactFromListEvent
import org.springframework.context.ApplicationEventPublisher
import org.springframework.context.event.EventListener
import org.springframework.stereotype.Component
import org.springframework.transaction.annotation.Propagation
import org.springframework.transaction.annotation.Transactional

@Component
class ContributionEventListener(
    private val eventPublisher: ApplicationEventPublisher
) {
    @EventListener
    @Transactional(propagation = Propagation.REQUIRES_NEW)
    fun onChange(evt: ContributionChangedEvent) {
        when (evt.changeType) {
            ContributionChangeType.CREATED,
            ContributionChangeType.UPDATED -> eventPublisher.publishEvent(
                AddContactToListEvent(evt.userId, evt.periodId)
            )
            ContributionChangeType.DELETED -> eventPublisher.publishEvent(
                RemoveContactFromListEvent(evt.userId, evt.periodId)
            )
        }
    }
}

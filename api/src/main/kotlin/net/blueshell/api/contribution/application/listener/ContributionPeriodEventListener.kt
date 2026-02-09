package net.blueshell.api.contribution.application.listener

import net.blueshell.api.contribution.application.event.ContributionPeriodChangedEvent
import net.blueshell.api.contribution.application.ContributionPeriodService
import net.blueshell.api.platform.integration.event.job.CreateContributionPeriodListEvent
import org.springframework.context.ApplicationEventPublisher
import org.springframework.context.event.EventListener
import org.springframework.stereotype.Component
import org.springframework.transaction.annotation.Propagation
import org.springframework.transaction.annotation.Transactional

@Component
class ContributionPeriodEventListener(
    private val eventPublisher: ApplicationEventPublisher,
    private val periods: ContributionPeriodService
) {
    @EventListener
    @Transactional(propagation = Propagation.REQUIRES_NEW)
    fun onChange(evt: ContributionPeriodChangedEvent) {
        val c = periods.findById(evt.periodId)
        if (c.listId != null) return
        eventPublisher.publishEvent(CreateContributionPeriodListEvent(c.id))
    }
}

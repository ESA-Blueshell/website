package net.blueshell.api.event.application.listener

import net.blueshell.api.event.application.EventSignUpService
import net.blueshell.api.event.application.event.EventSignUpCreatedEvent
import net.blueshell.api.platform.integration.event.job.EventSignupEmailEvent
import org.springframework.context.ApplicationEventPublisher
import org.springframework.context.event.EventListener
import org.springframework.stereotype.Component
import org.springframework.transaction.annotation.Propagation
import org.springframework.transaction.annotation.Transactional

@Component
class EventSignUpEventListener(
    private val eventPublisher: ApplicationEventPublisher,
    private val signUps: EventSignUpService
) {
    /**
     * send e-mail only if the transaction COMMITTED successfully
     */
    @EventListener
    @Transactional(propagation = Propagation.REQUIRES_NEW)
    fun onPersist(evt: EventSignUpCreatedEvent) {
        val e = signUps.findById(evt.signUpId)
        if (e.guest != null) {
            eventPublisher.publishEvent(EventSignupEmailEvent(e.id))
        }
    }
}

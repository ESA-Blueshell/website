package net.blueshell.api.listener.jpa

import lombok.RequiredArgsConstructor
import net.blueshell.api.common.event.job.EventSignupEmailEvent
import net.blueshell.api.common.event.jpa.PrePersistEvent
import net.blueshell.api.model.event.EventSignUp
import org.springframework.context.ApplicationEventPublisher
import org.springframework.stereotype.Component
import org.springframework.transaction.annotation.Propagation
import org.springframework.transaction.annotation.Transactional
import org.springframework.transaction.event.TransactionPhase
import org.springframework.transaction.event.TransactionalEventListener

@Component
@RequiredArgsConstructor
class EventSignUpEventListener {
    private val eventPublisher: ApplicationEventPublisher? = null

    /**
     * send e-mail only if the transaction COMMITTED successfully
     */
    @TransactionalEventListener(phase = TransactionPhase.AFTER_COMMIT)
    @Transactional(propagation = Propagation.REQUIRES_NEW)
    fun onPersist(evt: PrePersistEvent<EventSignUp>) {
        val e = evt.getSource()

        if (e.getGuest() != null) {
            eventPublisher!!.publishEvent(EventSignupEmailEvent(e.getId()))
        }
    }
}

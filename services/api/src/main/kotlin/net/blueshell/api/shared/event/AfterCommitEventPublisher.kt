package net.blueshell.api.shared.event

import org.springframework.context.ApplicationEventPublisher
import org.springframework.stereotype.Component
import org.springframework.transaction.support.TransactionSynchronization
import org.springframework.transaction.support.TransactionSynchronizationManager

@Component
class AfterCommitEventPublisher(
    private val publisher: ApplicationEventPublisher
) {
    fun publish(event: Any) {
        if (TransactionSynchronizationManager.isSynchronizationActive()) {
            TransactionSynchronizationManager.registerSynchronization(object : TransactionSynchronization {
                override fun afterCommit() {
                    publisher.publishEvent(event)
                }
            })
            return
        }

        publisher.publishEvent(event)
    }
}

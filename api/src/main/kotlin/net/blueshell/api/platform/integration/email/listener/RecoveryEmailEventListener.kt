package net.blueshell.api.platform.integration.email.listener

import net.blueshell.api.platform.integration.event.job.RecoveryEmailEvent
import net.blueshell.api.platform.integration.email.job.RecoveryEmailJob
import org.springframework.stereotype.Component
import org.springframework.transaction.event.TransactionPhase
import org.springframework.transaction.event.TransactionalEventListener

@Component
class RecoveryEmailEventListener(
    private val recoveryEmailJob: RecoveryEmailJob
) {
    @TransactionalEventListener(phase = TransactionPhase.AFTER_COMMIT)
    fun onReset(evt: RecoveryEmailEvent) {
        val userId = evt.userId

        recoveryEmailJob.send(userId, evt.token, evt.resetType)
    }
}

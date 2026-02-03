package net.blueshell.api.listener.email

import net.blueshell.api.common.event.job.RecoveryEmailEvent
import net.blueshell.api.job.email.RecoveryEmailJob
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
        if (evt.userId == null) return

        recoveryEmailJob.send(userId, evt.token, evt.resetType)
    }
}

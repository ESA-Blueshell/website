package net.blueshell.api.domain.auth.application.listener

import net.blueshell.api.domain.auth.application.UserActivationService
import net.blueshell.api.domain.user.application.event.UserCreated
import net.blueshell.api.platform.integration.queue.EmailJobs
import net.blueshell.api.platform.integration.queue.JobDispatcher
import org.springframework.context.event.EventListener
import org.springframework.stereotype.Component
import org.springframework.transaction.annotation.Propagation
import org.springframework.transaction.annotation.Transactional

@Component
class RecoveryEventListener(
    private val jobDispatcher: JobDispatcher,
    private val activationService: UserActivationService
) {

    /**
     * React to user creation: issue appropriate token and send mail.
     */
    @EventListener
    @Transactional(propagation = Propagation.REQUIRES_NEW)
    fun onUserCreated(event: UserCreated) {
        val dispatch = activationService.issueActivationForNewUser(event.userId, event.createdByBoard == true)
        jobDispatcher.enqueue(
            EmailJobs.Recovery,
            EmailJobs.RecoveryPayload(dispatch.userId, dispatch.rawToken, dispatch.type)
        )
    }
}

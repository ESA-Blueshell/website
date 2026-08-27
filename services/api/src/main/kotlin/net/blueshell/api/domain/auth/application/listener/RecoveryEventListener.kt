package net.blueshell.api.domain.auth.application.listener

import net.blueshell.api.domain.auth.application.UserActivationService
import net.blueshell.api.domain.user.application.event.UserCreated
import net.blueshell.api.shared.job.EmailJobs
import net.blueshell.api.shared.job.TrackedJobDispatcher
import org.springframework.context.event.EventListener
import org.springframework.stereotype.Component
import org.springframework.transaction.annotation.Propagation
import org.springframework.transaction.annotation.Transactional

@Component
class RecoveryEventListener(
    private val jobs: TrackedJobDispatcher,
    private val activationService: UserActivationService
) {

    /**
     * React to user creation: issue appropriate token and send mail.
     */
    @EventListener
    @Transactional(propagation = Propagation.REQUIRES_NEW)
    fun onUserCreated(event: UserCreated) {
        val dispatch = activationService.issueActivationForNewUser(event.userId, event.createdByBoard == true)
        jobs.runAsyncFromActor(
            EmailJobs.Recovery,
            EmailJobs.RecoveryPayload(dispatch.userId, dispatch.rawToken, dispatch.type),
            event
        )
    }
}

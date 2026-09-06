package net.blueshell.api.auth.domain

import net.blueshell.api.user.api.UserCreated
import net.blueshell.api.shared.job.EmailJobs
import net.blueshell.api.shared.job.JobQueue
import net.blueshell.api.shared.job.runAsyncFromActor
import org.slf4j.LoggerFactory
import org.springframework.context.event.EventListener
import org.springframework.stereotype.Component
import org.springframework.transaction.annotation.Propagation
import org.springframework.transaction.annotation.Transactional

@Component
class RecoveryEventListener(
    private val dispatcher: ActivationEmailDispatcher,
) {
    private val log = LoggerFactory.getLogger(javaClass)

    /**
     * React to user creation: issue the activation token and send the mail.
     *
     * The account is committed by the time this runs, so a throw would 500 a registration that
     * succeeded and leave the applicant no way in; a link that was never issued can be asked for
     * again, so this is logged and the registration stands. The catch must sit outside the
     * transaction — a failed write marks it rollback-only, and the commit then throws
     * `UnexpectedRollbackException` past any catch within it.
     */
    @EventListener
    fun onUserCreated(event: UserCreated) {
        try {
            dispatcher.dispatchFor(event)
        } catch (e: Exception) {
            log.error("Could not issue the activation email for user {}; it has to be asked for again", event.userId, e)
        }
    }
}

/**
 * The transaction the activation dispatch runs in, separated so its boundary is inside
 * the caller's `try` rather than around it.
 */
@Component
open class ActivationEmailDispatcher(
    private val jobs: JobQueue,
    private val activationService: UserActivationService,
) {
    @Transactional(propagation = Propagation.REQUIRES_NEW)
    open fun dispatchFor(event: UserCreated) {
        val dispatch = activationService.issueActivationForNewUser(event.userId, event.createdByBoard == true)
        jobs.runAsyncFromActor(
            EmailJobs.Recovery,
            EmailJobs.RecoveryPayload(dispatch.userId, dispatch.rawToken, dispatch.type),
            event,
        )
    }
}

package net.blueshell.api.auth.domain

import net.blueshell.api.user.api.UserCreated
import net.blueshell.api.shared.job.EmailJobs
import net.blueshell.api.shared.job.TrackedJobDispatcher
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
     * The account is committed by the time this runs — it publishes after commit, inside
     * the request — so a throw here answered the registration with a 500 for an account
     * that exists, leaving an applicant with no continuation token, no way in, and their
     * own name on every uniqueness rule they retried against. A link that was never
     * issued can be asked for again, so this failure is survivable and the response is
     * not: it is logged and the registration stands.
     *
     * The catch has to sit outside the transaction, not inside it. A write that fails
     * marks the transaction rollback-only, and the commit at the boundary then throws
     * `UnexpectedRollbackException` past a catch within it — the same 500, one frame out.
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
    private val jobs: TrackedJobDispatcher,
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

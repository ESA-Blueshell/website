package net.blueshell.api.auth.domain

import net.blueshell.api.shared.model.RecoveryEmailPreview
import net.blueshell.api.shared.model.SignupOutcome
import net.blueshell.api.shared.job.JobQueue
import net.blueshell.api.shared.enums.TokenPurpose
import net.blueshell.api.shared.job.EmailJobs
import org.springframework.stereotype.Service

/**
 * Password recovery and account activation.
 *
 * Every path that issues a token enqueues the email rather than sending it
 * inline, so a mail failure is retried rather than lost, and a request for an
 * unknown account produces no dispatch and therefore no email — which is what
 * stops these endpoints confirming whether an address exists.
 */
@Service
class RecoveryUseCases(
    private val passwordRecoveryService: PasswordRecoveryService,
    private val activationService: UserActivationService,
    private val completion: SignupCompletionService,
    private val previews: RecoveryEmailPreviewService,
    private val jobs: JobQueue,
) {
    fun resetPassword(username: String) =
        enqueueRecoveryEmail(passwordRecoveryService.requestPasswordReset(username))

    fun setPassword(token: String, password: String) =
        passwordRecoveryService.setPassword(token, password)

    fun activateUser(token: String): SignupOutcome {
        val user = activationService.activateUser(token)
        // The other half of the rendezvous may already be in place, in which case
        // this is the write that starts the membership (ADR-025).
        return completion.completeIfReady(user.id!!)
    }

    fun activateMember(token: String, username: String, password: String) =
        activationService.activateMember(token, username, password)

    fun resendUserActivation(username: String) =
        enqueueRecoveryEmail(activationService.requestUserActivation(username))

    fun resendRecoveryEmail(userId: Long, purpose: TokenPurpose?) =
        enqueueRecoveryEmail(
            purpose?.let { activationService.requestActivation(userId, it) }
                ?: activationService.requestActivationEmail(userId),
        )

    fun pendingActivations(): Map<Long, TokenPurpose> = activationService.pendingActivations()

    fun previewRecoveryEmail(userId: Long, purpose: TokenPurpose): RecoveryEmailPreview =
        previews.preview(userId, purpose)

    private fun enqueueRecoveryEmail(dispatch: RecoveryDispatch?) {
        if (dispatch == null) return
        jobs.runAsync(
            EmailJobs.Recovery,
            EmailJobs.RecoveryPayload(dispatch.userId, dispatch.rawToken, dispatch.type),
        )
    }
}

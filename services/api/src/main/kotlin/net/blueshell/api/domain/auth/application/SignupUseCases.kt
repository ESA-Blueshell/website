package net.blueshell.api.domain.auth.application

import net.blueshell.api.shared.model.SignupSession
import net.blueshell.api.domain.user.application.UserService
import net.blueshell.api.shared.job.TrackedJobDispatcher
import net.blueshell.api.shared.job.EmailJobs
import org.springframework.http.HttpStatus
import org.springframework.security.access.AccessDeniedException
import org.springframework.stereotype.Service
import org.springframework.transaction.annotation.Transactional
import org.springframework.web.server.ResponseStatusException

/** The two signup operations the auth module owns: issuing a session, and correcting the address it was sent to. */
@Service
class SignupUseCases(
    private val signupTokens: SignupTokenService,
    private val users: UserService,
    private val activation: UserActivationService,
    private val jobs: TrackedJobDispatcher,
) {
    fun issueSession(userId: Long): SignupSession = signupTokens.issue(users.findById(userId))

    @Transactional
    fun correctEmail(signupToken: String, email: String) {
        val account = signupTokens.resolveAccount(signupToken)
        if (account.user.enabled) {
            throw AccessDeniedException("A confirmed email address is changed through account settings")
        }
        // Checked here rather than by a declarative validator because the account is
        // only known once the token resolves — the precedent set in ADR-024.
        if (users.existsByEmailAndIdNot(email, account.id)) {
            throw ResponseStatusException(HttpStatus.CONFLICT, "That email address is already in use")
        }
        account.user.email = email
        users.update(account.user)
        // requestUserActivation retires whatever was outstanding before issuing the
        // replacement. That matters most here: the mistyped address may be somebody
        // else's inbox, so a link already delivered there has to stop working.
        val dispatch = requireNotNull(activation.requestUserActivation(account.user.username)) {
            "Expected an activation dispatch for the unconfirmed account ${account.id}"
        }
        jobs.enqueue(
            EmailJobs.Recovery,
            EmailJobs.RecoveryPayload(dispatch.userId, dispatch.rawToken, dispatch.type),
        )
    }
}

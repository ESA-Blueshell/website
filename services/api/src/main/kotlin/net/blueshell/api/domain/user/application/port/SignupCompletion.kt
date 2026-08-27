package net.blueshell.api.domain.user.application.port

import net.blueshell.api.shared.model.SignupOutcome

/**
 * Commits a signup once every prerequisite holds, reporting what did and did not
 * complete.
 *
 * Declared here and implemented by the auth module, which owns the signup. A
 * membership application depends on the outcome inside its own transaction — it
 * refuses the application when nothing started — so this is a call rather than an
 * event.
 */
interface SignupCompletion {
    fun completeIfReady(userId: Long): SignupOutcome
}

package net.blueshell.api.security

import org.springframework.stereotype.Component
import java.time.Duration

/** Refused because the sign-in was not proved within the step-up window. */
class StepUpRequiredException : RuntimeException("Confirm it is you before changing this")

/**
 * Asks the current sign-in to have been proved in the last ten minutes, before a change to how
 * the person signs in or an admin's reset of somebody else's (`docs/CONTEXT.md`, step-up).
 */
@Component
class StepUp(
    private val signIns: SignIns,
) {
    fun require() {
        val signIn = SignInContext.current()?.let { signIns.find(it.id) } ?: throw StepUpRequiredException()
        if (!signIns.steppedUpWithin(signIn, WINDOW)) throw StepUpRequiredException()
    }

    companion object {
        val WINDOW: Duration = Duration.ofMinutes(10)
    }
}

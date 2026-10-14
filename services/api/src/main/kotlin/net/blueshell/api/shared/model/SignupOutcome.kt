package net.blueshell.api.shared.model

// Shared because both the membership application (user domain) and email
// confirmation (auth domain) return it — see ADR-025.
data class SignupOutcome(
    val emailConfirmed: Boolean,
    val membershipStarted: Boolean,
    /** The first contribution is collected by the next incasso run rather than asked by transfer. */
    val collectedByIncasso: Boolean = false,
)

package net.blueshell.api.security

import java.time.Instant

/**
 * One browser's time signed in, held on the server (api ADR-030). The auth cookie names it by
 * [id] and carries a token id that must be [currentJti], or [previousJti] within its grace.
 */
data class SignIn(
    val id: String,
    val userId: Long,
    val startedAt: Instant,
    val lastSeenAt: Instant,
    val browser: Browser,
    val securityStamp: Long,
    val currentJti: String,
    val currentIssuedAt: Instant,
    val previousJti: String? = null,
    val previousRetiredAt: Instant? = null,
    /** When a second factor was last given in this sign-in: at the challenge or a step-up. */
    val secondFactorAt: Instant? = null,
    /** The `amr` values of how this sign-in was made. */
    val methods: Set<String> = setOf(METHOD_PASSWORD),
) {
    companion object {
        const val METHOD_PASSWORD = "pwd"
        const val METHOD_OTP = "otp"
    }
}

/** Why a sign-in ended without its owner asking. */
enum class SignInEndReason {
    /** A token id that is neither current nor within its grace: two holders of one cookie. */
    REUSED,

    /** A request from another browser family or operating system than the sign-in began in. */
    BROWSER_CHANGED,
}

/** Published when a sign-in is ended because it looked stolen, so the owner can be told. */
data class SignInEndedAsSuspicious(
    val userId: Long,
    val reason: SignInEndReason,
    val browser: Browser,
    val at: Instant,
)

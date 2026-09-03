package net.blueshell.api.user.api

/**
 * What makes a password acceptable, in one place because four request types and the
 * registration rule all state it and a copy that drifts refuses a password every
 * rule the applicant was shown had accepted.
 *
 * The complexity rules are a lower bound on what a password must contain, never an
 * upper bound on what it may: an allowlist of symbols refuses passwords stronger
 * than the ones it accepts. On `user`'s published surface rather than the kernel,
 * since a password belongs to an account.
 *
 * Mirrored by the `hasLower`, `hasUpper`, `hasNumber` and `hasSpecial` rules in
 * `services/frontend/src/plugins/validation.ts`, which name this file back. A
 * change here belongs there in the same commit.
 */
object PasswordPolicy {
    const val COMPLEXITY_REGEX = "^(?=.*[a-z])(?=.*[A-Z])(?=.*\\d)(?=.*[^A-Za-z\\d]).+$"

    const val COMPLEXITY_MESSAGE =
        "Password must contain at least one lowercase letter, one uppercase letter, one number " +
            "and one special character"

    const val MIN_LENGTH = 8
    const val MAX_LENGTH = 100

    const val LENGTH_MESSAGE = "Password must be at least $MIN_LENGTH characters"
}

package net.blueshell.api.shared.validation

/**
 * What makes a password acceptable, in one place because four request types and the
 * registration rule all state it and a copy that drifts is a rejection nobody can
 * act on.
 *
 * The complexity requirements are a lower bound on what a password must contain.
 * They are deliberately not an upper bound on what it *may* contain: an allowlist
 * of permitted symbols refuses a password that is stronger than the ones it
 * accepts, and the form that collects it cannot warn about a character it has no
 * reason to think is special. Anything at all counts as the fourth character
 * class, so long as it is not a letter or a digit.
 *
 * The frontend mirrors this in the `hasLower`, `hasUpper`, `hasNumber` and
 * `hasSpecial` rules in `services/frontend/src/plugins/validation.ts`, which name
 * this file. A change here belongs there in the same commit.
 */
object PasswordPolicy {
    const val COMPLEXITY_REGEX = "^(?=.*[a-z])(?=.*[A-Z])(?=.*\\d)(?=.*[^A-Za-z\\d]).+$"

    const val COMPLEXITY_MESSAGE =
        "Password must contain at least one lowercase letter, one uppercase letter, one number " +
            "and one special character"

    const val MIN_LENGTH = 8
    const val MAX_LENGTH = 100
}

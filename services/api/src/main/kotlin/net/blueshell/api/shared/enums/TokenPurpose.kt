package net.blueshell.api.shared.enums

import io.swagger.v3.oas.annotations.media.Schema

/** Values are persisted in `recovery_tokens.type`, so they are schema and must not be renamed. */
@Schema(enumAsRef = true)
enum class TokenPurpose {
    USER_ACTIVATION,

    // Removable, along with its handling, once every member has activated their account.
    MEMBER_ACTIVATION,

    PASSWORD_RESET,
    SIGNUP_CONTINUATION,

    /** The "wasn't me" link a security notification carries. Following it locks the account. */
    ACCOUNT_LOCK,

    /** Sent to a new address; following it moves the account there. */
    EMAIL_CHANGE,

    /** Sent by a two-factor reset; with the password, it signs the person in to set up again. */
    TWO_FACTOR_REENROLMENT,
    ;

    /** Whether issuing one retires the unconsumed ones of its kind. A lock link must not. */
    val retiresEarlier: Boolean get() = this != ACCOUNT_LOCK

    /**
     * Whether a recovery email carries this token. A signup continuation never leaves the site
     * (ADR-024); a lock link travels inside the security notification it belongs to.
     */
    val isMailable: Boolean get() = this != SIGNUP_CONTINUATION && this != ACCOUNT_LOCK

    /** Whether this token activates an account, as opposed to recovering one. */
    val isActivation: Boolean get() = this == USER_ACTIVATION || this == MEMBER_ACTIVATION
}

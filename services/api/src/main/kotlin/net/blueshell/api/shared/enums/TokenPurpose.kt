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
    ACCOUNT_LOCK,
    EMAIL_CHANGE,
    TWO_FACTOR_REENROLMENT,
    ;

    val retiresEarlier: Boolean get() = this != ACCOUNT_LOCK

    val isMailable: Boolean get() = this != SIGNUP_CONTINUATION && this != ACCOUNT_LOCK

    /** Whether this token activates an account, as opposed to recovering one. */
    val isActivation: Boolean get() = this == USER_ACTIVATION || this == MEMBER_ACTIVATION
}

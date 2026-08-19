package net.blueshell.api.shared.enums

import io.swagger.v3.oas.annotations.media.Schema

// Values are persisted in recovery_tokens.type, so they are schema and must not
// be renamed.
@Schema(enumAsRef = true)
enum class TokenPurpose {
    USER_ACTIVATION,
    MEMBER_ACTIVATION,  // TODO: Once all members have activated their accounts, remove member activation type and all handling
    PASSWORD_RESET,
    SIGNUP_CONTINUATION,
}

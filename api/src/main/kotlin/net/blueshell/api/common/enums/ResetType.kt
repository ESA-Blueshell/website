package net.blueshell.api.common.enums

import io.swagger.v3.oas.annotations.media.Schema

@Schema(enumAsRef = true)
enum class ResetType {
    USER_ACTIVATION,
    MEMBER_ACTIVATION,  // TODO: Once all members have activated their accounts, remove member activation type and all handling
    PASSWORD_RESET
}

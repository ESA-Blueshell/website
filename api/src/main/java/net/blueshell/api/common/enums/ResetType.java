package net.blueshell.api.common.enums;

import io.swagger.v3.oas.annotations.media.Schema;

@Schema(enumAsRef = true)
public enum ResetType {
    USER_ACTIVATION,
    MEMBER_ACTIVATION,
    PASSWORD_RESET
}

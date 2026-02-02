package net.blueshell.api.common.enums

import io.swagger.v3.oas.annotations.media.Schema

@Schema(enumAsRef = true)
enum class PlatformType {
    FACEBOOK,
    LINKEDIN,
    TWITTER,
    INSTAGRAM
}

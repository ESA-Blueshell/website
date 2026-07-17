package net.blueshell.api.domain.user.web.dto.response

import io.swagger.v3.oas.annotations.media.Schema

@Schema(enumAsRef = true)
enum class Gender {
    M,
    F,
    X,
}

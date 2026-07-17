package net.blueshell.api.domain.board.web.dto.response

import io.swagger.v3.oas.annotations.media.Schema

@Schema(enumAsRef = true)
enum class BoardRole {
    CHAIR,
    TREASURER,
    SECRETARY,
    GENERAL,
}

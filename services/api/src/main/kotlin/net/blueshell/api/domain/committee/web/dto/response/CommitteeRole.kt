package net.blueshell.api.domain.committee.web.dto.response

import io.swagger.v3.oas.annotations.media.Schema

@Schema(enumAsRef = true)
enum class CommitteeRole {
    CHAIR,
    MEMBER,
}

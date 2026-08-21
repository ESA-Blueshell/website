package net.blueshell.api.domain.committee.web.dto.request

import io.swagger.v3.oas.annotations.media.Schema
import jakarta.validation.constraints.NotBlank

@Schema(name = "CommitteeMemberRequest")
data class CommitteeMemberRequest(
    var userId: Long,

    @field:NotBlank
    var role: String
)

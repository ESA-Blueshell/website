package net.blueshell.api.domain.committee.web.dto.request

import io.swagger.v3.oas.annotations.media.Schema
import jakarta.validation.constraints.NotBlank
import jakarta.validation.constraints.NotNull

@Schema(name = "CommitteeMemberRequest")
data class CommitteeMemberRequest(
    @field:NotNull
    var userId: Long? = null,

    @field:NotBlank
    var role: String? = null
)

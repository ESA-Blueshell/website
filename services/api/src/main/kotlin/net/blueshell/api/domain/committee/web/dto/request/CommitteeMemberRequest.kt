package net.blueshell.api.domain.committee.web.dto.request

import io.swagger.v3.oas.annotations.media.Schema
import jakarta.validation.constraints.Size

@Schema(name = "CommitteeMemberRequest")
data class CommitteeMemberRequest(
    var userId: Long,

    @field:Schema(
        description = "What this member does on the committee. Omitted for a member who " +
            "simply sits on it, which is most of them.",
        example = "Chair",
    )
    @field:Size(max = 50, message = "Role must be at most 50 characters")
    var role: String? = null
)

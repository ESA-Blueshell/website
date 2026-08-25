package net.blueshell.api.domain.committee.web.dto.request

import io.swagger.v3.oas.annotations.media.Schema

@Schema(name = "CommitteeMemberRequest")
data class CommitteeMemberRequest(
    var userId: Long,

    @field:Schema(
        description = "What this member does on the committee. Omitted for a member who " +
            "simply sits on it, which is most of them.",
        example = "Chair",
    )
    var role: String? = null
)

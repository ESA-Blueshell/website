package net.blueshell.api.domain.committee.web.dto.response

import io.swagger.v3.oas.annotations.media.Schema
import jakarta.validation.constraints.NotBlank
import jakarta.validation.constraints.NotNull
import java.time.Instant

@Schema(name = "CommitteeMemberResponse")
data class CommitteeMemberResponse(
    @field:NotNull
    var userId: Long,

    @field:NotNull
    var committeeId: Long,

    @field:NotNull
    var role: CommitteeRole,
    var version: Long,
    var createdAt: Instant,
    var updatedAt: Instant
)

package net.blueshell.api.committee.web

import io.swagger.v3.oas.annotations.media.Schema
import jakarta.validation.constraints.NotNull
import java.time.Instant

@Schema(name = "CommitteeMemberResponse")
data class CommitteeMemberResponse(
    @field:NotNull
    var userId: Long,
    @field:NotNull
    var committeeId: Long,
    // A seat without a stated role is the common case, so the field stays null rather than empty.
    var role: String?,
    var version: Long,
    var createdAt: Instant,
    var updatedAt: Instant,
)

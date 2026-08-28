package net.blueshell.api.committee.web

import io.swagger.v3.oas.annotations.media.Schema
import jakarta.validation.Valid
import jakarta.validation.constraints.NotBlank
import jakarta.validation.constraints.NotEmpty
import jakarta.validation.constraints.NotNull
import jakarta.validation.constraints.Size
import java.time.Instant

@Schema(name = "CommitteeDetailResponse")
data class CommitteeDetailResponse(
    var id: Long,

    @field:NotBlank(message = "Committee name cannot be blank.")
    @field:Size(max = 255, message = "Committee name cannot exceed 255 characters.")
    var name: String,

    @field:NotBlank(message = "Committee description cannot be empty.")
    @field:Size(max = 4095, message = "Committee description cannot exceed 4095 characters.")
    var description: String,

    @field:NotNull
    @field:NotEmpty
    @field:Valid
    var members: MutableList<CommitteeMemberResponse>,
    var version: Long,
    var createdAt: Instant,
    var updatedAt: Instant
) : CommitteeResponse

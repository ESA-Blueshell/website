package net.blueshell.api.domain.committee.web.dto.request

import io.swagger.v3.oas.annotations.media.Schema
import jakarta.validation.Valid
import jakarta.validation.constraints.NotBlank
import jakarta.validation.constraints.NotEmpty
import jakarta.validation.constraints.NotNull
import jakarta.validation.constraints.Size

@Schema(name = "UpdateCommitteeRequest")
data class UpdateCommitteeRequest(
    @field:NotBlank(message = "Committee name cannot be blank.")
    @field:Size(max = 255, message = "Committee name cannot exceed 255 characters.")
    var name: String? = null,

    @field:NotBlank(message = "Committee description cannot be empty.")
    @field:Size(max = 4095, message = "Committee description cannot exceed 4095 characters.")
    var description: String? = null,

    @field:NotNull
    @field:NotEmpty
    @field:Valid
    var members: MutableList<CommitteeMemberRequest>? = mutableListOf(),

    @field:NotNull
    var version: Long? = null
)

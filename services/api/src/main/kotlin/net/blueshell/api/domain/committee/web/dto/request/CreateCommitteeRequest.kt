package net.blueshell.api.domain.committee.web.dto.request

import io.swagger.v3.oas.annotations.media.Schema
import jakarta.validation.Valid
import jakarta.validation.constraints.NotBlank
import jakarta.validation.constraints.NotEmpty
import jakarta.validation.constraints.Size

@Schema(name = "CreateCommitteeRequest")
data class CreateCommitteeRequest(
    @field:NotBlank(message = "Committee name cannot be blank.")
    @field:Size(max = 255, message = "Committee name cannot exceed 255 characters.")
    var name: String,

    @field:NotBlank(message = "Committee description cannot be empty.")
    @field:Size(max = 4095, message = "Committee description cannot exceed 4095 characters.")
    var description: String,

    @field:NotEmpty
    @field:Valid
    var members: MutableList<CommitteeMemberRequest> = mutableListOf()
)

package net.blueshell.api.committee.web

import io.swagger.v3.oas.annotations.media.Schema
import jakarta.validation.Valid
import jakarta.validation.constraints.NotBlank
import jakarta.validation.constraints.NotEmpty
import jakarta.validation.constraints.Size
import net.blueshell.api.shared.model.DESCRIPTION_MAX

@Schema(name = "CreateCommitteeRequest")
data class CreateCommitteeRequest(
    @field:NotBlank(message = "Committee name cannot be blank.")
    @field:Size(min = 1, max = 100, message = "Name must be 1-100 characters")
    var name: String,
    @field:NotBlank(message = "Committee description cannot be empty.")
    @field:Size(min = 1, max = DESCRIPTION_MAX, message = "Description must be 1-$DESCRIPTION_MAX characters")
    var description: String,
    @field:NotEmpty
    @field:Valid
    var members: MutableList<CommitteeMemberRequest> = mutableListOf(),
)

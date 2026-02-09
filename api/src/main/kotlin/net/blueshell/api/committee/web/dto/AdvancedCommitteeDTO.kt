package net.blueshell.api.committee.web.dto

import io.swagger.v3.oas.annotations.media.Schema
import jakarta.validation.constraints.NotBlank
import jakarta.validation.constraints.NotEmpty
import jakarta.validation.constraints.Size
import net.blueshell.api.shared.dto.AuditedAutoIdDTO

@Schema(name = "AdvancedCommittee")
data class AdvancedCommitteeDTO(
    @field:NotBlank(message = "Committee name cannot be blank.")
    @field:Size(max = 255, message = "Committee name cannot exceed 255 characters.")
    var name: String? = null,

    @field:NotBlank(message = "Committee description cannot be empty.")
    @field:Size(max = 4095, message = "Committee description cannot exceed 4095 characters.")
    var description: String? = null,

    @field:NotEmpty
    var members: MutableList<CommitteeMemberDTO> = mutableListOf()
) : AuditedAutoIdDTO()

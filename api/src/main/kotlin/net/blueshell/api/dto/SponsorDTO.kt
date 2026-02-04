package net.blueshell.api.dto

import io.swagger.v3.oas.annotations.media.Schema
import jakarta.validation.constraints.NotBlank
import jakarta.validation.constraints.Size
import net.blueshell.api.base.dto.AuditedAutoIdDTO

@Schema(name = "Sponsor")
data class SponsorDTO(
    @field:NotBlank(message = "Sponsor name cannot be blank.")
    @field:Size(max = 255, message = "Sponsor name cannot exceed 255 characters.")
    var name: String? = null,

    @field:NotBlank(message = "Sponsor description cannot be empty.")
    @field:Size(max = 4095, message = "Sponsor description cannot exceed 4095 characters.")
    var description: String? = null
) : AuditedAutoIdDTO()

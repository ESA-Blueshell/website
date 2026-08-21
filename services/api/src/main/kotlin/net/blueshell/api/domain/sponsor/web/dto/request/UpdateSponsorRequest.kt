package net.blueshell.api.domain.sponsor.web.dto.request

import io.swagger.v3.oas.annotations.media.Schema
import jakarta.validation.constraints.NotBlank
import jakarta.validation.constraints.Size

@Schema(name = "UpdateSponsorRequest")
data class UpdateSponsorRequest(
    @field:NotBlank(message = "Sponsor name cannot be blank.")
    @field:Size(max = 255, message = "Sponsor name cannot exceed 255 characters.")
    var name: String,

    @field:NotBlank(message = "Sponsor description cannot be empty.")
    @field:Size(max = 4095, message = "Sponsor description cannot exceed 4095 characters.")
    var description: String,

    var version: Long
)

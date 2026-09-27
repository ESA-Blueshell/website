package net.blueshell.api.sponsor.web

import io.swagger.v3.oas.annotations.media.Schema
import jakarta.validation.constraints.NotBlank
import jakarta.validation.constraints.Size
import net.blueshell.api.shared.model.DESCRIPTION_MAX

@Schema(name = "CreateSponsorRequest")
data class CreateSponsorRequest(
    @field:NotBlank(message = "Sponsor name cannot be blank.")
    @field:Size(max = 255, message = "Sponsor name cannot exceed 255 characters.")
    var name: String,
    @field:NotBlank(message = "Sponsor description cannot be empty.")
    @field:Size(max = DESCRIPTION_MAX, message = "Sponsor description cannot exceed $DESCRIPTION_MAX characters.")
    var description: String,
)

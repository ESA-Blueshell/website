package net.blueshell.api.dto

import io.swagger.v3.oas.annotations.media.Schema
import jakarta.validation.constraints.NotBlank
import jakarta.validation.constraints.Size
import net.blueshell.api.base.BaseDTO
@Schema(name = "Sponsor")
class SponsorDTO : BaseDTO() {
    @NotBlank(message = "Sponsor name cannot be blank.")
    @Size(max = 255, message = "Sponsor name cannot exceed 255 characters.")
    val name: @NotBlank(message = "Sponsor name cannot be blank.") @Size(
        max = 255,
        message = "Sponsor name cannot exceed 255 characters."
    ) String? = null

    @NotBlank(message = "Sponsor description cannot be empty.")
    @Size(max = 4095, message = "Sponsor description cannot exceed 4095 characters.")
    val description: @NotBlank(message = "Sponsor description cannot be empty.") @Size(
        max = 4095,
        message = "Sponsor description cannot exceed 4095 characters."
    ) String? = null
}

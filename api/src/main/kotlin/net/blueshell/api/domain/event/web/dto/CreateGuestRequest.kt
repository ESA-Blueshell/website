package net.blueshell.api.domain.event.web.dto

import io.swagger.v3.oas.annotations.media.Schema
import jakarta.validation.constraints.NotBlank
import net.blueshell.api.shared.dto.BaseDTO

@Schema(name = "CreateGuestRequest")
data class CreateGuestRequest(
    @field:NotBlank(message = "Guest name cannot be empty.")
    var name: String? = null,

    @field:NotBlank(message = "Guest discord cannot be empty.")
    var discord: String? = null,

    @field:NotBlank(message = "Guest email cannot be empty.")
    var email: String? = null,

    var phoneNumber: String? = null,

    var version: Long? = null
) : BaseDTO()

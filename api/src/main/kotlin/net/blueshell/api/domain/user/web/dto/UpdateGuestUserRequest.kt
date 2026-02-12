package net.blueshell.api.domain.user.web.dto

import io.swagger.v3.oas.annotations.media.Schema
import jakarta.validation.constraints.NotBlank
import jakarta.validation.constraints.NotNull

@Schema(name = "UpdateGuestUserRequest")
data class UpdateGuestUserRequest(
    @field:NotBlank
    var discord: String? = null,

    @field:NotBlank
    var phoneNumber: String? = null,

    @field:NotNull
    var newsletter: Boolean? = null,

    var version: Long? = null
)

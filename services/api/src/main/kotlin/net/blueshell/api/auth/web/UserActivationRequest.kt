package net.blueshell.api.auth.web

import io.swagger.v3.oas.annotations.media.Schema
import jakarta.validation.constraints.NotBlank

@Schema(name = "UserActivationRequest")
data class UserActivationRequest(
    @field:NotBlank
    var token: String
)

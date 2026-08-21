package net.blueshell.api.domain.auth.web.dto.request

import io.swagger.v3.oas.annotations.media.Schema
import jakarta.validation.constraints.NotBlank

@Schema(name = "UserActivationRequest")
data class UserActivationRequest(
    @field:NotBlank
    var token: String
)

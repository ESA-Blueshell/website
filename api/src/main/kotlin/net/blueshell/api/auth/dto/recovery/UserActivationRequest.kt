package net.blueshell.api.auth.dto.recovery

import io.swagger.v3.oas.annotations.media.Schema
import jakarta.validation.constraints.NotBlank
import net.blueshell.api.shared.dto.BaseDTO

@Schema(name = "UserActivationRequest")
data class UserActivationRequest(
    @field:NotBlank
    var token: String? = null
) : BaseDTO()

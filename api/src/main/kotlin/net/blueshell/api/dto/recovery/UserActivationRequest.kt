package net.blueshell.api.dto.recovery

import io.swagger.v3.oas.annotations.media.Schema
import jakarta.validation.constraints.NotBlank
import net.blueshell.api.base.BaseDTO

@Schema(name = "UserActivationRequest")
data class UserActivationRequest(
    @field:NotBlank
    var token: String? = null
) : BaseDTO()

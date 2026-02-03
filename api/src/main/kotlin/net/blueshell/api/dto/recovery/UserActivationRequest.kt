package net.blueshell.api.dto.recovery

import io.swagger.v3.oas.annotations.media.Schema
import jakarta.validation.constraints.NotBlank
import net.blueshell.api.base.BaseDTO
@Schema(name = "UserActivationRequest")
class UserActivationRequest : BaseDTO() {
    @NotBlank
    val token: @NotBlank String? = null
}

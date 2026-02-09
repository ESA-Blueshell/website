package net.blueshell.api.auth.api.dto.recovery

import io.swagger.v3.oas.annotations.media.Schema
import jakarta.validation.constraints.NotBlank
import jakarta.validation.constraints.Pattern
import jakarta.validation.constraints.Size
import net.blueshell.api.shared.dto.BaseDTO
import net.blueshell.api.user.api.validation.UniqueUsername

@Schema(name = "MemberActivationRequest")
data class MemberActivationRequest(
    @field:NotBlank
    var token: String? = null,

    @field:NotBlank
    @field:UniqueUsername
    var username: String? = null,

    @field:Size(min = 8, max = 100, message = "Password must be at least 8 characters")
    @field:Pattern(
        regexp = "^(?=.*[a-z])(?=.*[A-Z])(?=.*\\d)(?=.*[@$!%*?&])[A-Za-z\\d@$!%*?&]+$",
        message = "Password must contain at least one lowercase letter, one uppercase letter, one number, and one special character (@$!%*?&)"
    )
    var password: String? = null
) : BaseDTO()

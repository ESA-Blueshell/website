package net.blueshell.api.domain.auth.web.dto.request

import io.swagger.v3.oas.annotations.media.Schema
import jakarta.validation.constraints.NotBlank
import jakarta.validation.constraints.Pattern
import jakarta.validation.constraints.Size

@Schema(name = "PasswordResetRequest")
data class PasswordResetRequest(
    @field:NotBlank
    var token: String,

    @field:NotBlank
    @field:Size(min = 8, max = 100, message = "Password must be at least 8 characters")
    @field:Pattern(
        regexp = "^(?=.*[a-z])(?=.*[A-Z])(?=.*\\d)(?=.*[@$!%*?&])[A-Za-z\\d@$!%*?&]+$",
        message = "Password must contain at least one lowercase letter, one uppercase letter, one number, and one special character (@$!%*?&)"
    )
    var password: String
)

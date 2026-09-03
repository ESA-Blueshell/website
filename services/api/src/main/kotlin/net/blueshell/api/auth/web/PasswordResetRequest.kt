package net.blueshell.api.auth.web

import io.swagger.v3.oas.annotations.media.Schema
import jakarta.validation.constraints.NotBlank
import jakarta.validation.constraints.Pattern
import jakarta.validation.constraints.Size
import net.blueshell.api.shared.validation.PasswordPolicy

@Schema(name = "PasswordResetRequest")
data class PasswordResetRequest(
    @field:NotBlank
    var token: String,

    @field:NotBlank
    @field:Size(
        min = PasswordPolicy.MIN_LENGTH,
        max = PasswordPolicy.MAX_LENGTH,
        message = "Password must be at least 8 characters"
    )
    @field:Pattern(
        regexp = PasswordPolicy.COMPLEXITY_REGEX,
        message = PasswordPolicy.COMPLEXITY_MESSAGE
    )
    var password: String
)

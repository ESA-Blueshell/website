package net.blueshell.api.auth.web

import io.swagger.v3.oas.annotations.media.Schema
import jakarta.validation.constraints.NotBlank
import jakarta.validation.constraints.Pattern
import jakarta.validation.constraints.Size
import net.blueshell.api.user.api.PasswordPolicy

@Schema(name = "MemberActivationRequest")
data class MemberActivationRequest(
    @field:NotBlank
    var token: String,

    @field:NotBlank
    var username: String,

    @field:Size(
        min = PasswordPolicy.MIN_LENGTH,
        max = PasswordPolicy.MAX_LENGTH,
        message = PasswordPolicy.LENGTH_MESSAGE
    )
    @field:Pattern(
        regexp = PasswordPolicy.COMPLEXITY_REGEX,
        message = PasswordPolicy.COMPLEXITY_MESSAGE
    )
    @field:NotBlank
    var password: String
)

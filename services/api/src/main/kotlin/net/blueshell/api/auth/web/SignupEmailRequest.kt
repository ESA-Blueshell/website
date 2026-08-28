package net.blueshell.api.auth.web

import io.swagger.v3.oas.annotations.media.Schema
import jakarta.validation.constraints.Email
import jakarta.validation.constraints.NotBlank

@Schema(name = "SignupEmailRequest")
data class SignupEmailRequest(
    @field:NotBlank
    @field:Email
    var email: String
)

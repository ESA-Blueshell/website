package net.blueshell.api.auth.web

import io.swagger.v3.oas.annotations.media.Schema
import jakarta.validation.constraints.Email
import jakarta.validation.constraints.NotBlank
import jakarta.validation.constraints.Pattern
import jakarta.validation.constraints.Size
import net.blueshell.api.user.api.PasswordPolicy

/** A code where two-factor is on, the password where it is not. */
@Schema(name = "StepUpRequest")
data class StepUpRequest(
    val code: String? = null,
    val password: String? = null,
)

/** The password, which only a granted role waiting on two-factor may leave out. */
@Schema(name = "TwoFactorSetUpRequest")
data class TwoFactorSetUpRequest(
    @field:Size(min = 1)
    val password: String? = null,
)

@Schema(name = "CodeRequest")
data class CodeRequest(
    @field:NotBlank
    val code: String,
)

@Schema(name = "PasswordChangeRequest")
data class PasswordChangeRequest(
    @field:NotBlank
    val currentPassword: String,
    @field:NotBlank
    @field:Size(min = PasswordPolicy.MIN_LENGTH, max = PasswordPolicy.MAX_LENGTH, message = PasswordPolicy.LENGTH_MESSAGE)
    @field:Pattern(regexp = PasswordPolicy.COMPLEXITY_REGEX, message = PasswordPolicy.COMPLEXITY_MESSAGE)
    val newPassword: String,
)

@Schema(name = "EmailChangeRequest")
data class EmailChangeRequest(
    @field:NotBlank
    @field:Email
    val email: String,
)

@Schema(name = "ReasonRequest")
data class ReasonRequest(
    @field:NotBlank
    @field:Size(max = 1000)
    val reason: String,
)

@Schema(name = "UnlockRequest")
data class UnlockRequest(
    @field:NotBlank
    @field:Size(max = 1000)
    val reason: String,
    @field:Email
    val email: String? = null,
)

@Schema(name = "TokenRequest")
data class TokenRequest(
    @field:NotBlank
    val token: String,
)

package net.blueshell.api.user.web

import io.swagger.v3.oas.annotations.media.Schema
import jakarta.validation.Valid
import jakarta.validation.constraints.NotBlank
import jakarta.validation.constraints.Pattern
import jakarta.validation.constraints.Size
import net.blueshell.api.shared.validation.PasswordPolicy

@Schema(name = "CreateUserRequest")
class CreateUserRequest(
    @field:NotBlank
    var username: String,

    @field:NotBlank
    var initials: String,

    @field:NotBlank
    var firstName: String,

    var prefix: String? = null,

    @field:NotBlank
    var lastName: String,

    var fullName: String? = null,

    var newsletter: Boolean,

    var consentPrivacy: Boolean? = null,

    var photoConsent: Boolean? = null,

    @field:NotBlank
    var email: String,

    @field:NotBlank
    var discord: String,

    @field:NotBlank
    var phoneNumber: String,

    @field:Valid
    var memberProfile: UpsertMemberProfileRequest? = null,

    @field:NotBlank(groups = [Creation::class])
    @field:Size(
        min = PasswordPolicy.MIN_LENGTH,
        max = PasswordPolicy.MAX_LENGTH,
        message = "Password must be at least 8 characters",
        groups = [Creation::class]
    )
    @field:Pattern(
        regexp = PasswordPolicy.COMPLEXITY_REGEX,
        message = PasswordPolicy.COMPLEXITY_MESSAGE,
        groups = [Creation::class]
    )
    var password: String? = null,
)

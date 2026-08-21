package net.blueshell.api.domain.user.web.dto.request

import io.swagger.v3.oas.annotations.media.Schema
import jakarta.validation.Valid
import jakarta.validation.constraints.NotBlank
import jakarta.validation.constraints.Pattern
import jakarta.validation.constraints.Size
import net.blueshell.api.shared.validation.group.Creation

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
        min = 8,
        max = 100,
        message = "Password must be at least 8 characters",
        groups = [Creation::class]
    )
    @field:Pattern(
        regexp = "^(?=.*[a-z])(?=.*[A-Z])(?=.*\\d)(?=.*[@$!%*?&])[A-Za-z\\d@$!%*?&]+$",
        message = "Password must contain at least one lowercase letter, one uppercase letter, one number, and one special character (@$!%*?&)",
        groups = [Creation::class]
    )
    var password: String? = null,
)

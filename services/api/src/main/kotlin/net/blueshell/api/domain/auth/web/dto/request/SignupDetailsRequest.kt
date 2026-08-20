package net.blueshell.api.domain.auth.web.dto.request

import io.swagger.v3.oas.annotations.media.Schema
import jakarta.validation.Valid
import jakarta.validation.constraints.NotBlank
import jakarta.validation.constraints.NotNull
import net.blueshell.api.domain.user.web.dto.request.UpsertMemberProfileRequest

/**
 * Everything the first signup step collects except the email address, which
 * changes through PATCH /signup/email because it invalidates the confirmation
 * link, and the password, which the applicant resets once signed in.
 */
@Schema(name = "SignupDetailsRequest")
class SignupDetailsRequest(
    @field:NotBlank
    var username: String? = null,

    @field:NotBlank
    var initials: String? = null,

    @field:NotBlank
    var firstName: String? = null,

    var prefix: String? = null,

    @field:NotBlank
    var lastName: String? = null,

    @field:NotBlank
    var discord: String? = null,

    @field:NotBlank
    var phoneNumber: String? = null,

    @field:NotNull
    var newsletter: Boolean? = null,

    var photoConsent: Boolean? = null,

    @field:Valid
    var memberProfile: UpsertMemberProfileRequest? = null
)

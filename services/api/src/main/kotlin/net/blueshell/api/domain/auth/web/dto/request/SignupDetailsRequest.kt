package net.blueshell.api.domain.auth.web.dto.request

import io.swagger.v3.oas.annotations.media.Schema
import jakarta.validation.Valid
import jakarta.validation.constraints.NotBlank
import net.blueshell.api.domain.user.web.dto.request.UpsertMemberProfileRequest

/**
 * Everything the first signup step collects except the email address, which
 * changes through PATCH /signup/email because it invalidates the confirmation
 * link, and the password, which the applicant resets once signed in.
 */
@Schema(name = "SignupDetailsRequest")
class SignupDetailsRequest(
    @field:NotBlank
    var username: String,

    @field:NotBlank
    var initials: String,

    @field:NotBlank
    var firstName: String,

    var prefix: String? = null,

    @field:NotBlank
    var lastName: String,

    @field:NotBlank
    var discord: String,

    @field:NotBlank
    var phoneNumber: String,

    var newsletter: Boolean,

    var photoConsent: Boolean? = null,

    @field:Valid
    var memberProfile: UpsertMemberProfileRequest? = null
)

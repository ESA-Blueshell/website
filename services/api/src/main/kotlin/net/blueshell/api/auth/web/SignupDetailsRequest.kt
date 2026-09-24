package net.blueshell.api.auth.web

import io.swagger.v3.oas.annotations.media.Schema
import jakarta.validation.Valid
import jakarta.validation.constraints.NotBlank
import net.blueshell.api.user.web.UpsertMemberProfileRequest
import net.blueshell.api.shared.util.SNOWFLAKE
import jakarta.validation.constraints.Pattern

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
    @field:Schema(description = "The Discord user ID of the member picked from the server, where one is picked")
    @field:Pattern(regexp = SNOWFLAKE, message = "A Discord user ID is a number")
    var discordId: String? = null,
    @field:NotBlank
    var phoneNumber: String,
    var newsletter: Boolean,
    var photoConsent: Boolean? = null,
    @field:Valid
    var memberProfile: UpsertMemberProfileRequest? = null,
)

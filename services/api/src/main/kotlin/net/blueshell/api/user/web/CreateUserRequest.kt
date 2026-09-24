package net.blueshell.api.user.web

import io.swagger.v3.oas.annotations.media.Schema
import jakarta.validation.Valid
import jakarta.validation.constraints.NotBlank
import jakarta.validation.constraints.Pattern
import jakarta.validation.constraints.Size
import net.blueshell.api.user.api.PasswordPolicy
import net.blueshell.api.shared.util.SNOWFLAKE

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
    @field:Schema(description = "The Discord user ID of the member picked from the server, where one is picked")
    @field:Pattern(regexp = SNOWFLAKE, message = "A Discord user ID is a number")
    var discordId: String? = null,
    @field:NotBlank
    var phoneNumber: String,
    @field:Valid
    var memberProfile: UpsertMemberProfileRequest? = null,
    @field:NotBlank(groups = [Creation::class])
    @field:Size(
        min = PasswordPolicy.MIN_LENGTH,
        max = PasswordPolicy.MAX_LENGTH,
        message = PasswordPolicy.LENGTH_MESSAGE,
        groups = [Creation::class],
    )
    @field:Pattern(
        regexp = PasswordPolicy.COMPLEXITY_REGEX,
        message = PasswordPolicy.COMPLEXITY_MESSAGE,
        groups = [Creation::class],
    )
    var password: String? = null,
)

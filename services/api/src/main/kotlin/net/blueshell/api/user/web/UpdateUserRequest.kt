package net.blueshell.api.user.web

import io.swagger.v3.oas.annotations.media.Schema
import jakarta.validation.Valid
import jakarta.validation.constraints.NotBlank
import jakarta.validation.constraints.Pattern
import net.blueshell.api.shared.util.SNOWFLAKE

@Schema(name = "UpdateUserRequest")
open class UpdateUserRequest(
    var newsletter: Boolean,
    var photoConsent: Boolean? = null,
    @field:NotBlank
    var discord: String,
    @field:Schema(description = "The Discord user ID of the member picked from the server, where one is picked")
    @field:Pattern(regexp = SNOWFLAKE, message = "A Discord user ID is a number")
    var discordId: String? = null,
    @field:NotBlank
    var phoneNumber: String,
    @field:Valid
    var memberProfile: UpsertMemberProfileRequest? = null,
    var version: Long,
) : UpdateUserPayload

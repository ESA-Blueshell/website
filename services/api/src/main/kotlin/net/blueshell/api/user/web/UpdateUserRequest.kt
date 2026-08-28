package net.blueshell.api.user.web

import io.swagger.v3.oas.annotations.media.Schema
import jakarta.validation.Valid
import jakarta.validation.constraints.NotBlank

@Schema(name = "UpdateUserRequest")
open class UpdateUserRequest(
    var newsletter: Boolean,

    var photoConsent: Boolean? = null,

    @field:NotBlank
    var discord: String,

    @field:NotBlank
    var phoneNumber: String,

    @field:Valid
    var memberProfile: UpsertMemberProfileRequest? = null,

    var version: Long
) : UpdateUserPayload

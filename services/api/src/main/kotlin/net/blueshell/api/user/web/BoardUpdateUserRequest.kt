package net.blueshell.api.user.web

import io.swagger.v3.oas.annotations.media.Schema
import jakarta.validation.constraints.NotBlank

@Schema(name = "BoardUpdateUserRequest")
class BoardUpdateUserRequest(
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
    var email: String,

    newsletter: Boolean,

    discord: String,

    phoneNumber: String,

    version: Long,

    photoConsent: Boolean? = null,

    memberProfile: UpsertMemberProfileRequest? = null,
) : UpdateUserRequest(
    newsletter = newsletter,
    photoConsent = photoConsent,
    discord = discord,
    phoneNumber = phoneNumber,
    memberProfile = memberProfile,
    version = version,
)
